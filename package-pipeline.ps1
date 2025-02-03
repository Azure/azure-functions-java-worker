param (
    [string]$buildNumber
)

# A helper function that stops the entire script if the last command failed.
function StopOnFailedExecution {
    if ($LastExitCode) {
        exit $LastExitCode
    }
}

# --------------------------------------------------------------------
# Build the azure-functions-java-worker (using the "appinsights" profile)
# --------------------------------------------------------------------
Write-Host "=== Building azure-functions-java-worker with 'appinsights' profile ==="
mvn clean package --no-transfer-progress -B -P appinsights
StopOnFailedExecution

# --------------------------------------------------------------------
# Prepare the final "pkg" folder and copy core worker artifacts
# --------------------------------------------------------------------
Write-Host "`n=== Creating NuGet package: Microsoft.Azure.Functions.JavaWorker ==="
Write-Host "Using buildNumber: $buildNumber"

# Ensure 'nuget' command is available
Get-Command nuget | Out-Null
StopOnFailedExecution

Write-Host "Removing old 'pkg' folder (if present)..."
Remove-Item -Recurse -Force -ErrorAction Ignore .\pkg

Write-Host "Creating new 'pkg' folder..."
New-Item -ItemType Directory -Path .\pkg | Out-Null

Write-Host "Copying azure-functions-java-worker.jar to 'pkg'..."
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar','*tests.jar' |
        ForEach-Object { Copy-Item $_.FullName .\pkg\azure-functions-java-worker.jar }
StopOnFailedExecution

Write-Host "Copying supporting files into 'pkg' folder..."
Copy-Item .\worker.config.json .\pkg\
Copy-Item .\tools\AzureFunctionsJavaWorker.nuspec .\pkg\
Copy-Item .\annotationLib .\pkg\annotationLib -Recurse

# --------------------------------------------------------------------
# Locate the Application Insights agent built by the Maven profile
# --------------------------------------------------------------------
$AgentSourcePath = Join-Path $PSScriptRoot 'target\agent\applicationinsights-agent.jar'
if (!(Test-Path -Path $AgentSourcePath)) {
    Write-Host "`nERROR: Application Insights agent not found at '$AgentSourcePath'."
    Write-Host "Make sure you enabled the 'appinsights' Maven profile."
    exit 1
}

# --------------------------------------------------------------------
# Create a local 'agent' folder and copy the agent jar there
# --------------------------------------------------------------------
Write-Host "`n=== Setting up the agent folder ==="

$AgentFolder = Join-Path $PSScriptRoot 'agent'
$AgentFilename = 'applicationinsights-agent.jar'
$PackagedAgentFile = Join-Path $AgentFolder $AgentFilename

Write-Host "Removing old 'agent' folder (if present)..."
if (Test-Path -Path $AgentFolder) {
    Remove-Item -Recurse -Force $AgentFolder
}

Write-Host "Creating a new 'agent' folder..."
New-Item -ItemType Directory -Path $AgentFolder | Out-Null

Write-Host "Copying agent from '$AgentSourcePath' to '$PackagedAgentFile'..."
Copy-Item $AgentSourcePath -Destination $PackagedAgentFile
StopOnFailedExecution

# --------------------------------------------------------------------
# Remove signature files and adjust MANIFEST.MF in-place (no full extraction)
# --------------------------------------------------------------------
Write-Host "`n=== Removing signature files from '$PackagedAgentFile' ==="

# Load .NET assemblies for ZipArchive on Windows
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem

$fileStream = [System.IO.File]::Open($PackagedAgentFile, [System.IO.FileMode]::Open)
$zipArchive = New-Object System.IO.Compression.ZipArchive($fileStream, [System.IO.Compression.ZipArchiveMode]::Update)

try {
    Write-Host "Deleting signature files from META-INF..."
    $entriesToRemove = $zipArchive.Entries | Where-Object {
        $_.FullName -like "META-INF/MSFTSIG.*" `
        -or $_.FullName -like "META-INF/*.SF" `
        -or $_.FullName -like "META-INF/*.RSA" `
        -or $_.FullName -like "META-INF/*.DSA"
    }

    foreach ($entry in $entriesToRemove) {
        Write-Host "  Removing: $($entry.FullName)"
        $entry.Delete()
    }

    Write-Host "Checking MANIFEST.MF for extra signature references..."
    $manifestEntry = $zipArchive.Entries | Where-Object { $_.FullName -eq "META-INF/MANIFEST.MF" }
    if ($manifestEntry) {
        $reader = New-Object System.IO.StreamReader($manifestEntry.Open())
        $manifestContent = $reader.ReadToEnd()
        $reader.Close()

        # Regex to remove blank line(s) after the main attributes
        $pattern = '(?sm)^(.*?\r?\n)\r?\n'
        $matches = [regex]::Matches($manifestContent, $pattern)

        if ($matches.Count -gt 0) {
            Write-Host "  Removing signature-related lines after main attributes."
            $cleanedManifest = $matches[0].Groups[1].Value

            $manifestEntry.Delete()

            $newManifestEntry = $zipArchive.CreateEntry("META-INF/MANIFEST.MF")
            $writer = New-Object System.IO.StreamWriter($newManifestEntry.Open())
            $writer.Write($cleanedManifest)
            $writer.Flush()
            $writer.Close()

            Write-Host "  MANIFEST.MF updated successfully."
        }
        else {
            Write-Host "  No extra blank lines found in MANIFEST.MF."
        }
    }
    else {
        Write-Host "No MANIFEST.MF found in the JAR (unexpected?)."
    }
}
finally {
    # Always dispose archive and file streams
    $zipArchive.Dispose()
    $fileStream.Dispose()
}

Write-Host "Done removing signature files from '$PackagedAgentFile'."

# --------------------------------------------------------------------
# Add 'functions.codeless' marker and copy agent folder to 'pkg'
# --------------------------------------------------------------------
Write-Host "`n=== Creating 'functions.codeless' marker file ==="
New-Item -Path $AgentFolder -Name "functions.codeless" -ItemType File | Out-Null

Write-Host "Copying 'agent' folder into the 'pkg' folder..."
Copy-Item $AgentFolder (Join-Path $PSScriptRoot 'pkg\agent') -Recurse -Force -Verbose

# --------------------------------------------------------------------
# Package everything into the final NuGet package
# --------------------------------------------------------------------
Write-Host "`n=== Creating the NuGet package ==="
Push-Location pkg
nuget pack -Properties version=$buildNumber
Pop-Location

Write-Host "`n=== Script completed successfully. NuGet package created. ==="
