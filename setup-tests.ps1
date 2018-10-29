
# A function that checks exit codes and fails script if an error is found 
function StopOnFailedExecution {
  if ($LastExitCode) 
  { 
    exit $LastExitCode 
  }
}

$currDir =  Get-Location

Write-Host "Deleting Functions Core Tools if exists...."
Remove-Item -Force ./Azure.Functions.Cli.zip -ErrorAction Ignore
Remove-Item -Recurse -Force ./Azure.Functions.Cli -ErrorAction Ignore

Write-Host "Downloading Functions Core Tools...."
Invoke-RestMethod -Uri 'https://functionsclibuilds.blob.core.windows.net/builds/2/latest/version.txt' -OutFile version.txt
Write-Host "Using Functions Core Tools version: $(Get-Content -Raw version.txt)"
Remove-Item version.txt

$url = "https://functionsclibuilds.blob.core.windows.net/builds/2/latest/Azure.Functions.Cli.win-x86.zip"
$output = "$currDir\Azure.Functions.Cli.zip"
$wc = New-Object System.Net.WebClient
$wc.DownloadFile($url, $output)

Write-Host "Extracting Functions Core Tools...."
Expand-Archive ".\Azure.Functions.Cli.zip" -DestinationPath ".\Azure.Functions.Cli"

Write-Host "Copying azure-functions-java-worker to  Functions Host workers directory...."
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName ".\Azure.Functions.Cli\workers\java\azure-functions-java-worker.jar" }
Copy-Item ".\worker.config.json" ".\Azure.Functions.Cli\workers\java"

Write-Host "Building endtoendtests...."
$Env:Path = $Env:Path+";$currDir\Azure.Functions.Cli"
Push-Location -Path "./endtoendtests" -StackName javaWorkerDir
Write-Host "Building azure-functions-maven-com.microsoft.azure.functions.endtoendtests" 
cmd.exe /c '.\..\mvnBuildSkipTests.bat'
StopOnFailedExecution
Pop-Location -StackName "javaWorkerDir"

Write-Host "Copying EventHubs function.json as temporary workaround...."
Copy-Item ".\endtoendtests\functionInputJson.json" ".\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests\EventHubTriggerAndOutputJSON\function.json"
Copy-Item ".\endtoendtests\functionInputString.json" ".\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests\EventHubTriggerAndOutputString\function.json"