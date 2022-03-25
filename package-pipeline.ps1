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
cmd.exe /c '.\mvnBuild.bat'
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
Copy-Item ".\lib_worker_1.6.2" pkg\lib -Recurse
Copy-Item ".\annotationLib" pkg\annotationLib -Recurse
set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..