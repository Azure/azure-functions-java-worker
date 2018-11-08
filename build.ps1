
# A function that checks exit codes and fails script if an error is found 
function StopOnFailedExecution {
  if ($LastExitCode) 
  { 
    exit $LastExitCode 
  }
}

$skipCliDownload = $args[0]

$currDir =  Get-Location

Write-Host "Building azure-function-java-worker"
cmd.exe /c '.\mvnBuild.bat'
StopOnFailedExecution

Write-Host "Starting azure-functions-java-endtoendtests execution"
Write-Host s$cliDownload
.\setup-tests.ps1 $skipCliDownload
$proc = start-process -filepath $currDir\Azure.Functions.Cli\func.exe -WorkingDirectory "$currDir\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests" -ArgumentList "host start" -PassThru

# wait for host to start
Start-Sleep -s 30
.\run-tests.ps1