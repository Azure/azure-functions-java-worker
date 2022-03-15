#
# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE file in the project root for full license information.
#
param
(
    [Switch]
    $UseCoreToolsBuildFromIntegrationTests
)

$FUNC_RUNTIME_VERSION = '4'
$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
$os = if ($IsWindows) { "win" } else { if ($IsMacOS) { "osx" } else { "linux" } }

$env:CORE_TOOLS_URL = $null
$coreToolsUrl = $null
if ($UseCoreToolsBuildFromIntegrationTests.IsPresent)
{
    Write-Host "Install the Core Tools for Integration Tests..."
    $env:CORE_TOOLS_URL = "https://functionsintegclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest/Azure.Functions.Cli.$os-$arch.zip"
    $coreToolsUrl = "https://functionsintegclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest"
}
else
{
    Write-Host "Install the Core Tools..."
    $env:CORE_TOOLS_URL = "https://functionsclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest/Azure.Functions.Cli.$os-$arch.zip"
    $coreToolsUrl = "https://functionsclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest"
}

$FUNC_CLI_DIRECTORY = Join-Path $PSScriptRoot 'Azure.Functions.Cli'

$ApplicationInsightsAgentVersion = '3.2.8'
$ApplicationInsightsAgentUrl = "https://github.com/microsoft/ApplicationInsights-Java/releases/download/$ApplicationInsightsAgentVersion/applicationinsights-agent-$ApplicationInsightsAgentVersion.jar"

Write-Host 'Deleting the Core Tools if exists...'
Remove-Item -Force "$FUNC_CLI_DIRECTORY.zip" -ErrorAction Ignore
Remove-Item -Recurse -Force $FUNC_CLI_DIRECTORY -ErrorAction Ignore

$version = Invoke-RestMethod -Uri "$coreToolsUrl/version.txt"
Write-Host "Downloading the Core Tools (Version: $version)..."

$output = "$FUNC_CLI_DIRECTORY.zip"
Write-Host "Downloading the Core Tools from url: $env:CORE_TOOLS_URL"
Invoke-RestMethod -Uri $env:CORE_TOOLS_URL -OutFile $output

Write-Host 'Extracting Core Tools...'
Expand-Archive $output -DestinationPath $FUNC_CLI_DIRECTORY

if (-not $UseCoreToolsBuildFromIntegrationTests.IsPresent)
{
    Write-Host "Replacing Java worker binaries in the Core Tools..."
    Get-ChildItem -Path "$PSScriptRoot/target/*" -Include 'azure*' -Exclude '*shaded.jar','*tests.jar' | ForEach-Object {
      Copy-Item $_.FullName "$FUNC_CLI_DIRECTORY/workers/java/azure-functions-java-worker.jar" -Force -Verbose
    }

    Write-Host "Copying worker.config.json to worker directory"
    Copy-Item "$PSScriptRoot/worker.config.json" "$FUNC_CLI_DIRECTORY/workers/java" -Force -Verbose
    Write-Host "Copying worker.config.json and annotationLib to worker directory"
    Copy-Item "$PSScriptRoot/annotationLib" "$FUNC_CLI_DIRECTORY/workers/java/annotationLib" -Recurse -Verbose
    mkdir "$FUNC_CLI_DIRECTORY/workers/java/agent"
    Write-Host "Downloading Application Insights Agent (Version: $ApplicationInsightsAgentVersion) from url($ApplicationInsightsAgentUrl) to worker directory"
    Invoke-RestMethod -Uri $ApplicationInsightsAgentUrl -OutFile "$FUNC_CLI_DIRECTORY/workers/java/agent/applicationinsights-agent.jar"
    Write-Host "Creating the functions.codeless file"
    New-Item -path "$FUNC_CLI_DIRECTORY/workers/java/agent" -type file -name "functions.codeless"
}
