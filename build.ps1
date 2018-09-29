
# A function that checks exit codes and fails script if an error is found 
function StopOnFailedExecution {
  if ($LastExitCode) 
  { 
    exit $LastExitCode 
  }
}

Write-Host "Building azure-function-java-worker"
mvn clean package 
StopOnFailedExecution
Write-Host "Building azure-functions-java-endtoendtests"
Push-Location -Path .\endtoendtests
mvn clean package 
StopOnFailedExecution
Pop-Location

Write-Host "Starting azure-functions-java-endtoendtests execution"
.\run-tests-local.ps1