#
# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE file in the project root for full license information.
#
param
(
    [Switch]
    $UseCoreToolsBuildFromIntegrationTests
)

$FUNC_RUNTIME_VERSION = '3'
$arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture.ToString().ToLowerInvariant()
$os = if ($IsWindows) { "win" } else { if ($IsMacOS) { "osx" } else { "linux" } }

$coreToolsDownloadURL = $null
if ($UseCoreToolsBuildFromIntegrationTests.IsPresent)
{
    $coreToolsDownloadURL = "https://functionsintegclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest/Azure.Functions.Cli.$os-$arch.zip"
    $env:CORE_TOOLS_URL = "https://functionsintegclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest"
}
else
{
    $coreToolsDownloadURL = "https://functionsclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest/Azure.Functions.Cli.$os-$arch.zip"
    if (-not $env:CORE_TOOLS_URL)
    {
        $env:CORE_TOOLS_URL = "https://functionsclibuilds.blob.core.windows.net/builds/$FUNC_RUNTIME_VERSION/latest"
    }
}

$FUNC_CLI_DIRECTORY = Join-Path $PSScriptRoot 'Azure.Functions.Cli'

Write-Host 'Deleting Functions Core Tools if exists...'
Remove-Item -Force "$FUNC_CLI_DIRECTORY.zip" -ErrorAction Ignore
Remove-Item -Recurse -Force $FUNC_CLI_DIRECTORY -ErrorAction Ignore

$version = Invoke-RestMethod -Uri "$env:CORE_TOOLS_URL/version.txt"
Write-Host "Downloading Functions Core Tools (Version: $version)..."

$output = "$FUNC_CLI_DIRECTORY.zip"
Invoke-RestMethod -Uri $coreToolsDownloadURL -OutFile $output

Write-Host 'Extracting Functions Core Tools...'
Expand-Archive $output -DestinationPath $FUNC_CLI_DIRECTORY

if (-not $UseCoreToolsBuildFromIntegrationTests.IsPresent)
{
  Write-Host "Copying azure-functions-java-worker to  Functions Host workers directory...."

  Get-ChildItem -Path "$PSScriptRoot/target/*" -Include 'azure*' -Exclude '*shaded.jar','*tests.jar' | ForEach-Object {
    Copy-Item $_.FullName "$FUNC_CLI_DIRECTORY/workers/java/azure-functions-java-worker.jar" -Force -Verbose
  }

  Copy-Item "$PSScriptRoot/worker.config.json" "$FUNC_CLI_DIRECTORY/workers/java" -Force -Verbose
  Copy-Item "$PSScriptRoot/lib_worker_1.6.2" "$FUNC_CLI_DIRECTORY/workers/java/lib" -Recurse -Verbose
}
