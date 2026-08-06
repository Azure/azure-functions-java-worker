#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Bootstraps the Azure Artifacts Maven credential provider for local development.

.DESCRIPTION
    Maven packages for this repository are restored from an Azure Artifacts feed. Reads are
    anonymous, so this script is only needed by Microsoft developers who have to ingest a package
    version that the feed has not cached yet.

    The script:
      1. Verifies the credential provider is present in the local Maven repository, and downloads it
         from the public AzureArtifacts tools feed if it is not.
      2. Writes '.mvn/extensions.xml' at the root of the repository so Maven loads the provider.

    '.mvn/' is intentionally listed in .gitignore. The extension exits when it detects a build
    context, and committing it would force an authenticated restore on anonymous consumers. Azure
    Pipelines uses the MavenAuthenticate@0 task instead.

.PARAMETER Version
    Version of the credential provider to install. Defaults to the version pinned by this script.

.PARAMETER LocalRepositoryPath
    Path to the local Maven repository. Defaults to '~/.m2/repository'.

.PARAMETER Force
    Overwrite an existing '.mvn/extensions.xml' even if it declares extensions this script does not
    manage, and re-download the credential provider even when it is already installed.

.EXAMPLE
    ./eng/scripts/Install-MavenCredentialProvider.ps1

.LINK
    https://eng.ms/docs/coreai/devdiv/one-engineering-system-1es/1es-docs/azure-artifacts/maven-credprovider
#>

[CmdletBinding()]
param(
    [string] $Version = '3.2.1',
    [string] $LocalRepositoryPath,
    [switch] $Force
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$groupId = 'com.microsoft.azure'
$artifactId = 'artifacts-maven-credprovider'
$bootstrapFeed = 'https://pkgs.dev.azure.com/artifacts-public/PublicTools/_packaging/AzureArtifacts/maven/v1'

# Maven records the extension against this repository id. It must match the <id> of the repositories
# declared in this repository's pom.xml files, otherwise resolution fails validation later.
$repositoryId = 'central'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven ('mvn') was not found on PATH. Install Apache Maven 3.0 or above and try again."
}

if (-not $LocalRepositoryPath) {
    $LocalRepositoryPath = Join-Path $HOME '.m2' 'repository'
}

$artifactDirectory = $LocalRepositoryPath
foreach ($segment in ($groupId.Split('.') + @($artifactId, $Version))) {
    $artifactDirectory = Join-Path $artifactDirectory $segment
}

$artifactPath = Join-Path $artifactDirectory "$artifactId-$Version.jar"

if ((Test-Path $artifactPath) -and -not $Force) {
    Write-Host "Credential provider $Version is already installed at '$artifactPath'."
}
else {
    Write-Host "Installing credential provider $Version from the public tools feed..."

    # The bootstrap must run outside of any Maven project so that this repository's own repository
    # and extension configuration does not take part in resolving the extension itself.
    $workingDirectory = Join-Path ([IO.Path]::GetTempPath()) ('credprovider-bootstrap-' + [Guid]::NewGuid().ToString('n'))
    New-Item -ItemType Directory -Path $workingDirectory -Force | Out-Null

    try {
        Push-Location $workingDirectory
        try {
            $mvnArgs = @(
                '--batch-mode'
                'dependency:get'
                "-Dartifact=${groupId}:${artifactId}:${Version}"
                "-DremoteRepositories=${repositoryId}::::${bootstrapFeed}"
            )

            if ($PSBoundParameters.ContainsKey('LocalRepositoryPath')) {
                $mvnArgs += "-Dmaven.repo.local=$LocalRepositoryPath"
            }

            & mvn @mvnArgs
            if ($LASTEXITCODE -ne 0) {
                throw "'mvn dependency:get' failed with exit code $LASTEXITCODE."
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        Remove-Item $workingDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }

    if (-not (Test-Path $artifactPath)) {
        throw "Bootstrap reported success but '$artifactPath' was not found. If a mirror is configured in your settings.xml, temporarily disable it and retry."
    }

    Write-Host "Installed credential provider to '$artifactPath'."
}

$extensionsDirectory = Join-Path $repoRoot '.mvn'
$extensionsPath = Join-Path $extensionsDirectory 'extensions.xml'

if ((Test-Path $extensionsPath) -and -not $Force) {
    $existing = Get-Content $extensionsPath -Raw

    if ($existing -notmatch [regex]::Escape($artifactId)) {
        throw "'$extensionsPath' already exists and declares extensions this script does not manage. Review it manually, or re-run with -Force to overwrite it."
    }

    if ($existing -match "<version>\s*$([regex]::Escape($Version))\s*</version>") {
        Write-Host "'$extensionsPath' is already configured for version $Version."
        Write-Host 'Done.'
        return
    }
}

$extensionsContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Generated by eng/scripts/Install-MavenCredentialProvider.ps1. Do not commit this file: it is
  ignored by .gitignore because the extension exits inside build environments and would break
  anonymous package restore for other consumers.
-->
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>$groupId</groupId>
    <artifactId>$artifactId</artifactId>
    <version>$Version</version>
  </extension>
</extensions>
"@

New-Item -ItemType Directory -Path $extensionsDirectory -Force | Out-Null
Set-Content -Path $extensionsPath -Value $extensionsContent -Encoding utf8

Write-Host "Wrote '$extensionsPath' for version $Version."
Write-Host 'Done.'
