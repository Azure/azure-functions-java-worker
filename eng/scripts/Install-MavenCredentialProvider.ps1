#!/usr/bin/env pwsh

<#
.SYNOPSIS
    Installs the Azure Artifacts Maven credential provider for local development.

.DESCRIPTION
    Anonymous restores work for packages already cached in the CFS feed. Microsoft developers can
    run this script to authenticate and ingest a package version that has not been cached yet.

.PARAMETER Version
    Credential provider version to install.

.PARAMETER LocalRepositoryPath
    Maven local repository path. Defaults to ~/.m2/repository.

.PARAMETER Force
    Reinstalls the provider and overwrites the generated .mvn/extensions.xml.
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
$repositoryId = 'central'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..' '..')).Path

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "Maven ('mvn') was not found on PATH."
}

$customLocalRepository = -not [string]::IsNullOrWhiteSpace($LocalRepositoryPath)
if (-not $customLocalRepository) {
    $LocalRepositoryPath = Join-Path $HOME '.m2' 'repository'
}

$artifactDirectory = $LocalRepositoryPath
foreach ($segment in ($groupId.Split('.') + @($artifactId, $Version))) {
    $artifactDirectory = Join-Path $artifactDirectory $segment
}
$artifactPath = Join-Path $artifactDirectory "$artifactId-$Version.jar"

if ($Force -or -not (Test-Path $artifactPath)) {
    $workingDirectory = Join-Path ([IO.Path]::GetTempPath()) ('maven-credprovider-' + [Guid]::NewGuid().ToString('n'))
    New-Item -ItemType Directory -Path $workingDirectory -Force | Out-Null
    try {
        Push-Location $workingDirectory
        try {
            $arguments = @(
                '--batch-mode'
                'dependency:get'
                "-Dartifact=${groupId}:${artifactId}:${Version}"
                "-DremoteRepositories=${repositoryId}::::${bootstrapFeed}"
            )
            if ($customLocalRepository) {
                $arguments += "-Dmaven.repo.local=$LocalRepositoryPath"
            }
            & mvn @arguments
            if ($LASTEXITCODE -ne 0) {
                throw "Maven failed with exit code $LASTEXITCODE."
            }
        }
        finally {
            Pop-Location
        }
    }
    finally {
        Remove-Item $workingDirectory -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $artifactPath)) {
    throw "Credential provider was not found at '$artifactPath' after installation."
}

$extensionsDirectory = Join-Path $repoRoot '.mvn'
$extensionsPath = Join-Path $extensionsDirectory 'extensions.xml'
if ((Test-Path $extensionsPath) -and -not $Force) {
    $existing = Get-Content $extensionsPath -Raw
    if ($existing -notmatch [regex]::Escape($artifactId)) {
        throw "'$extensionsPath' contains an unmanaged Maven extension. Use -Force to overwrite it."
    }
    if ($existing -match "<version>\s*$([regex]::Escape($Version))\s*</version>") {
        Write-Host "Maven credential provider $Version is already configured."
        return
    }
}

$extensions = @"
<?xml version="1.0" encoding="UTF-8"?>
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>$groupId</groupId>
    <artifactId>$artifactId</artifactId>
    <version>$Version</version>
  </extension>
</extensions>
"@

New-Item -ItemType Directory -Path $extensionsDirectory -Force | Out-Null
Set-Content -Path $extensionsPath -Value $extensions -Encoding utf8
Write-Host "Configured Maven credential provider $Version in '$extensionsPath'."