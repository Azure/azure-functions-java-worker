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

$ApplicationInsightsAgentVersion = '3.4.4'
$ApplicationInsightsAgentFilename = "applicationinsights-agent-${ApplicationInsightsAgentVersion}.jar"
$ApplicationInsightsAgentUrl = "https://repo1.maven.org/maven2/com/microsoft/azure/applicationinsights-agent/${ApplicationInsightsAgentVersion}/${ApplicationInsightsAgentFilename}"

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

    # Download application insights agent from maven central
    $ApplicationInsightsAgentFile = [System.IO.Path]::Combine($PSScriptRoot, $ApplicationInsightsAgentFilename)

    # local testing cleanup
    if (Test-Path -Path $ApplicationInsightsAgentFile) {
        Remove-Item -Path $ApplicationInsightsAgentFile
    }

    # local testing cleanup
    $oldOutput = [System.IO.Path]::Combine($PSScriptRoot, "agent")
    if (Test-Path -Path $oldOutput) {
        Remove-Item -Path $oldOutput -Recurse
    }

    # local testing cleanup
    $oldExtract = [System.IO.Path]::Combine($PSScriptRoot, "extract")
    if (Test-Path -Path $oldExtract) {
        Remove-Item -Path $oldExtract -Recurse
    }

    echo "Start downloading '$ApplicationInsightsAgentUrl' to '$PSScriptRoot'"
    try {
        Invoke-WebRequest -Uri $ApplicationInsightsAgentUrl -OutFile $ApplicationInsightsAgentFile
    } catch {
        echo "An error occurred. Download fails" $ApplicationInsightsAgentFile
        echo "Exiting"
        exit 1
    }

    if (-not(Test-Path -Path $ApplicationInsightsAgentFile)) {
        echo "$ApplicationInsightsAgentFile do not exist."
        exit 1
    }

    $extract = new-item -type directory -force $PSScriptRoot\extract
    if (-not(Test-Path -Path $extract)) {
        echo "Fail to create a new directory $extract"
        exit 1
    }

    echo "Start extracting content from $ApplicationInsightsAgentFilename to extract folder"
    cd -Path $extract -PassThru
    jar xf $ApplicationInsightsAgentFile
    cd $PSScriptRoot
    echo "Done extracting"

    echo "Unsign $ApplicationInsightsAgentFilename"
    Remove-Item $extract\META-INF\MSFTSIG.*
    $manifest = "$extract\META-INF\MANIFEST.MF"
    $newContent = (Get-Content -Raw $manifest | Select-String -Pattern '(?sm)^(.*?\r?\n)\r?\n').Matches[0].Groups[1].Value
    Set-Content -Path $manifest $newContent

    Remove-Item $ApplicationInsightsAgentFile
    if (-not(Test-Path -Path $ApplicationInsightsAgentFile)) {
        echo "Delete the original $ApplicationInsightsAgentFilename successfully"
    } else {
        echo "Fail to delete original source $ApplicationInsightsAgentFilename"
        exit 1
    }

    $agent = new-item -type directory -force $PSScriptRoot\agent
    $filename = "applicationinsights-agent.jar"
    $result = [System.IO.Path]::Combine($agent, $filename)
    echo "re-jar $filename"

    cd -Path $extract -PassThru
    jar cfm $result META-INF/MANIFEST.MF .

    if (-not(Test-Path -Path $result)) {
        echo "Fail to re-archive $filename"
        exit 1
    }

    Write-Host "Creating the functions.codeless file"
    New-Item -path $PSScriptRoot\agent -type file -name "functions.codeless"

    Write-Host "Copying the unsigned Application Insights Agent to worker directory"
    Copy-Item "$PSScriptRoot/agent" "$FUNC_CLI_DIRECTORY/workers/java/agent" -Recurse -Verbose
}
