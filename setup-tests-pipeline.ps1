#
# Copyright (c) Microsoft. All rights reserved.
# Licensed under the MIT license. See LICENSE file in the project root for full license information.
#
param
(
    [Switch]$UseCoreToolsBuildFromIntegrationTests
)

$FUNC_RUNTIME_VERSION = 'latest'

Write-Host "Installing Core Tools globlally using npm, version: $FUNC_RUNTIME_VERSION ..."

$env:NPM_CONFIG_USERCONFIG = if ($env:NPM_CONFIG_USERCONFIG) {
    $env:NPM_CONFIG_USERCONFIG
} else {
    Join-Path $PSScriptRoot '.npmrc'
}

$FUNC_CLI_DIRECTORY = Join-Path $PSScriptRoot 'Azure.Functions.Cli'
$InstallDir         = $FUNC_CLI_DIRECTORY

# 1. Clean previous install
Remove-Item -Recurse -Force $InstallDir -ErrorAction Ignore
New-Item -ItemType Directory -Path $InstallDir -ErrorAction Ignore

# 2. Locate the global prefix and module root that npm just used
$globalPrefix = (npm prefix -g | Out-String).Trim()         # e.g. /usr/local   or  C:\Users\<user>\AppData\Roaming\npm
$globalNode   = (npm root   -g | Out-String).Trim()         # e.g. /usr/local/lib/node_modules
$moduleRoot   = Join-Path $globalNode 'azure-functions-core-tools'

# 3. npm install → temp folder
npm install -g azure-functions-core-tools@$FUNC_RUNTIME_VERSION --unsafe-perm true --foreground-scripts --loglevel verbose

# 4. Copy CLI payload into the layout required by your tests
Copy-Item "$moduleRoot\bin\*" $InstallDir -Recurse -Force


if (-not $UseCoreToolsBuildFromIntegrationTests.IsPresent)
{
    Write-Host "Replacing Java worker binaries in the Core Tools..."
    Get-ChildItem -Path "$PSScriptRoot/target/*" -Include 'azure*' -Exclude '*shaded.jar','*tests.jar' | ForEach-Object {
      Copy-Item $_.FullName "$FUNC_CLI_DIRECTORY/workers/java/azure-functions-java-worker.jar" -Force -Verbose
    }

    Write-Host "Copying worker.config.json to worker directory"
    Copy-Item "$PSScriptRoot/worker.config.json" "$FUNC_CLI_DIRECTORY/workers/java" -Force -Verbose
    Write-Host "Copying worker.config.json and annotationLib to worker directory"
    Copy-Item "$PSScriptRoot/annotationLib" "$FUNC_CLI_DIRECTORY/workers/java" -Recurse -Verbose -Force
    Write-Host "Copying the unsigned Application Insights Agent to worker directory"
    Copy-Item "$PSScriptRoot/agent" "$FUNC_CLI_DIRECTORY/workers/java" -Recurse -Verbose -Force
}
