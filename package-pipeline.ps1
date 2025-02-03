param (
  [string]$buildNumber
)

# A function that checks exit codes and fails script if an error is found 
function StopOnFailedExecution {
  if ($LastExitCode) 
  {
    exit $LastExitCode 
  }
}

Write-Host "Building azure-functions-java-worker with appinsights profile"
mvn clean package --no-transfer-progress -B -P appinsights
StopOnFailedExecution

Write-Host "Creating nuget package Microsoft.Azure.Functions.JavaWorker"
Write-Host "buildNumber: " $buildNumber
Get-Command nuget
StopOnFailedExecution
remove-item pkg -Recurse -ErrorAction Ignore
mkdir pkg
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar','*tests.jar' | %{ Copy-Item $_.FullName .\pkg\azure-functions-java-worker.jar }
StopOnFailedExecution
copy-item ./worker.config.json pkg
copy-item ./tools/AzureFunctionsJavaWorker.nuspec pkg/
copy-item ./annotationLib pkg/annotationLib -Recurse

# locate the agent jar produced by the `appinsights` Maven profile
$ApplicationInsightsAgentFile = [System.IO.Path]::Combine($PSScriptRoot, 'target', 'agent', 'applicationinsights-agent.jar')

if (!(Test-Path -Path $ApplicationInsightsAgentFile)) {
    Write-Host "Error: $ApplicationInsightsAgentFile not found."
    Write-Host "Make sure you enabled the 'appinsights' Maven profile that copies the AI agent to target/agent/."
    exit 1
}

# local testing cleanup
$oldOutput = [System.IO.Path]::Combine($PSScriptRoot, "agent")
if (Test-Path -Path $oldOutput) {
    Remove-Item -Path $oldOutput -Recurse
}

$agent = new-item -type directory -force $PSScriptRoot\agent
$filename = "applicationinsights-agent.jar"
$packagedAgentFile = Join-Path $agent $filename

Copy-Item $ApplicationInsightsAgentFile -Destination $packagedAgentFile
Write-Host "Agent copied successfully to $packagedAgentFile"

# Load the required assembly for ZipArchive
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

Write-Host "Removing signature files from $packagedAgentFile ..."

# Open the jar as a zip archive in "Update" mode
$fileStream = [System.IO.File]::Open($packagedAgentFile, [System.IO.FileMode]::Open)
$zipArchive = New-Object System.IO.Compression.ZipArchive($fileStream, [System.IO.Compression.ZipArchiveMode]::Update)

try {
    # 1) Remove signature files (META-INF/MSFTSIG.*, .SF, .RSA, .DSA)
    Write-Host "Removing signature files..."
    $entriesToRemove = $zipArchive.Entries | Where-Object {
        $_.FullName -like "META-INF/MSFTSIG.*" `
        -or $_.FullName -like "META-INF/*.SF" `
        -or $_.FullName -like "META-INF/*.RSA" `
        -or $_.FullName -like "META-INF/*.DSA"
    }

    foreach ($entry in $entriesToRemove) {
        Write-Host "  Deleting: $($entry.FullName)"
        $entry.Delete()
    }

    # 2) Locate the MANIFEST.MF entry
    $manifestEntry = $zipArchive.Entries | Where-Object { $_.FullName -eq "META-INF/MANIFEST.MF" }
    if ($manifestEntry) {
        Write-Host "Removing signature references from MANIFEST.MF ..."

        # Read the existing manifest
        $reader = New-Object System.IO.StreamReader($manifestEntry.Open())
        $manifestContent = $reader.ReadToEnd()
        $reader.Close()

        $pattern = '(?sm)^(.*?\r?\n)\r?\n'
        $matches = [regex]::Matches($manifestContent, $pattern)

        if ($matches.Count -gt 0) {
            $cleanedManifest = $matches[0].Groups[1].Value

            # Delete the old MANIFEST.MF entry from the archive
            $manifestEntry.Delete()

            # Create a fresh entry for the updated MANIFEST.MF
            $newManifestEntry = $zipArchive.CreateEntry("META-INF/MANIFEST.MF")
            $writer = New-Object System.IO.StreamWriter($newManifestEntry.Open())
            $writer.Write($cleanedManifest)
            $writer.Flush()
            $writer.Close()

            Write-Host "MANIFEST.MF updated successfully."
        }
        else {
            Write-Host "No extra blank lines found. No changes to MANIFEST.MF."
        }
    }
    else {
        Write-Host "No MANIFEST.MF found in the JAR (unexpected?)."
    }
} finally {
    # Close out the archive and file streams
    $zipArchive.Dispose()
    $fileStream.Dispose()
}

Write-Host "Done removing signature files from $packagedAgentFile."

Write-Host "Creating the functions.codeless file"
New-Item -path $PSScriptRoot\agent -type file -name "functions.codeless"

cd $PSScriptRoot
Copy-Item $PSScriptRoot/agent $PSScriptRoot/pkg/agent -Recurse -Verbose

set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..
