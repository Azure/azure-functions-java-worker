#!/usr/bin/env pwsh

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string] $OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$keytool = Get-Command keytool -ErrorAction Stop
$javaHome = if ($env:JAVA_HOME) {
    $env:JAVA_HOME
}
else {
    Split-Path (Split-Path $keytool.Source -Parent) -Parent
}

$trustStore = @(
    (Join-Path $javaHome 'lib/security/cacerts')
    (Join-Path $javaHome 'jre/lib/security/cacerts')
) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } | Select-Object -First 1

if (-not $trustStore) {
    throw "Could not find the Java cacerts trust store under '$javaHome'."
}

$keytoolOutput = & $keytool.Source -list -rfc -keystore $trustStore -storepass changeit 2>&1
if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE.`n$($keytoolOutput -join [Environment]::NewLine)"
}

$certificates = [regex]::Matches(
    ($keytoolOutput -join "`n"),
    '-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----',
    [Text.RegularExpressions.RegexOptions]::Singleline)

if ($certificates.Count -eq 0) {
    throw "No PEM certificates were exported from '$trustStore'."
}

$resolvedOutputPath = [IO.Path]::GetFullPath($OutputPath)
$outputDirectory = Split-Path $resolvedOutputPath -Parent
New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
[IO.File]::WriteAllText(
    $resolvedOutputPath,
    (($certificates.Value -join "`n") + "`n"),
    [Text.UTF8Encoding]::new($false))

Write-Host "Exported $($certificates.Count) Java root certificates to '$resolvedOutputPath'."