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
Write-Host "Building azure-functions-java-worker" 
mvn clean package --no-transfer-progress -B
StopOnFailedExecution

Write-Host "Running Integration tests of azure-functions-java-worker"
mvn failsafe:integration-test --no-transfer-progress -B
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
set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..