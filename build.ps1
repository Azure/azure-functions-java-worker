
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

Write-Host "Starting azure-functions-java-endtoendtests execution"
.\run-tests-local.ps1