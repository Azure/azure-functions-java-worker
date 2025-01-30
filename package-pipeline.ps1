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

## local testing cleanup
#$oldExtract = [System.IO.Path]::Combine($PSScriptRoot, "extract")
#if (Test-Path -Path $oldExtract) {
#    Remove-Item -Path $oldExtract -Recurse
#}
#
#$extract = new-item -type directory -force $PSScriptRoot\extract
#if (-not(Test-Path -Path $extract)) {
#    echo "Fail to create a new directory $extract"
#    exit 1
#}
#
#echo "Start extracting content from $ApplicationInsightsAgentFile to extract folder"
#cd -Path $extract -PassThru
#jar xf $ApplicationInsightsAgentFile
#cd $PSScriptRoot
#echo "Done extracting"
#
#echo "Unsign $ApplicationInsightsAgentFile"
#Remove-Item $extract\META-INF\MSFTSIG.*
#$manifest = "$extract\META-INF\MANIFEST.MF"
#$newContent = (Get-Content -Raw $manifest | Select-String -Pattern '(?sm)^(.*?\r?\n)\r?\n').Matches[0].Groups[1].Value
#Set-Content -Path $manifest $newContent

$agent = new-item -type directory -force $PSScriptRoot\agent
$filename = "applicationinsights-agent.jar"
#$result = [System.IO.Path]::Combine($agent, $filename)
#echo "re-jar $filename"
#
#cd -Path $extract -PassThru
#jar cfm $result META-INF/MANIFEST.MF .
#
#if (-not(Test-Path -Path $result)) {
#    echo "Fail to re-archive $filename"
#    exit 1
#}

Copy-Item $ApplicationInsightsAgentFile -Destination (Join-Path $agent $filename)
Write-Host "Agent copied successfully to $agentDir"

Write-Host "Creating the functions.codeless file"
New-Item -path $PSScriptRoot\agent -type file -name "functions.codeless"

cd $PSScriptRoot
Copy-Item $PSScriptRoot/agent $PSScriptRoot/pkg/agent -Recurse -Verbose

set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..
