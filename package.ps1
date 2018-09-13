param (
  [string]$buildNumber = $env:APPVEYOR_BUILD_NUMBER
)

# A function that checks exit codes and fails script if an error is found 
function StopOnFailedExecution {
  if ($LastExitCode) 
  { 
    exit $LastExitCode 
  }
}

Write-Host "buildNumber: " $buildNumber
Get-Command mvn
Get-Command nuget
mvn clean install -DskipTests
StopOnFailedExecution
remove-item pkg -Recurse -ErrorAction Ignore
mkdir pkg
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName .\pkg\azure-functions-java-worker.jar }
StopOnFailedExecution
copy-item ./worker.config.json pkg
copy-item ./tools/AzureFunctionsJavaWorker.nuspec pkg/
set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..