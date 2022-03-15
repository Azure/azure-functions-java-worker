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

$ApplicationInsightsAgentVersion = '3.2.8'
$ApplicationInsightsAgentUrl = "https://github.com/microsoft/ApplicationInsights-Java/releases/download/$ApplicationInsightsAgentVersion/applicationinsights-agent-$ApplicationInsightsAgentVersion.jar"

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
copy-item ./annotationLib pkg/annotationLib -Recurse
mkdir pkg/agent
Write-Host "Downloading Application Insights Agent (Version: $ApplicationInsightsAgentVersion) from url($ApplicationInsightsAgentUrl)..."
Invoke-RestMethod -Uri $ApplicationInsightsAgentUrl -OutFile pkg/agent/applicationinsights-agent.jar
Write-Host "Creating the functions.codeless file"
New-Item -path pkg/agent -type file -name "functions.codeless"
set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..