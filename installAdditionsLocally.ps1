[CmdletBinding()]
param(
    [string]$AdditionsRepoUrl = 'https://github.com/Azure/azure-functions-java-additions.git',
    [string]$AdditionsBranch  = 'dev',
    [bool]$SkipTests = $true
)

$ErrorActionPreference = 'Stop'

$repoName       = 'azure-functions-java-additions'
$workerRoot     = $PSScriptRoot
$cloneDir       = Join-Path $workerRoot $repoName
$mvnBuildScript = Join-Path $workerRoot 'mvnBuildAdditions.bat'

# CI bootstrap only needs artifacts installed into the local Maven cache.
# Skipping tests avoids JDK-matrix-specific test compilation failures in additions.
$skipTestArgs = if ($SkipTests) { ' -Dmaven.test.skip=true' } else { '' }

Write-Host "Installing $repoName from $AdditionsRepoUrl (branch: $AdditionsBranch)"
Write-Host "Clone destination: $cloneDir"

# Make the clone idempotent for re-runs.
if (Test-Path $cloneDir) {
    Write-Host "Removing existing $cloneDir"
    Remove-Item -Path $cloneDir -Recurse -Force
}

Push-Location $workerRoot
try {
    git clone --branch $AdditionsBranch --single-branch $AdditionsRepoUrl
    if ($LASTEXITCODE -ne 0) { throw "git clone failed for $AdditionsRepoUrl ($AdditionsBranch)" }

    Push-Location $repoName
    try {
        # spotbugs-maven-plugin:3.1.6 bundles groovy-3.0.0-alpha-3 which crashes at class-load
        # time on JDK 17+ (ExceptionInInitializerError in org.codehaus.groovy.vmplugin.v7.Java7).
        # This happens before Maven can check -Dspotbugs.skip=true, so the skip flag is useless.
        # Work around by building additions with Java 8 when JAVA_HOME_8_X64 is available (always
        # set on ADO agents by the JavaToolInstaller pre-step). Falls back to current JAVA_HOME
        # on developer machines that don't have that variable set.
        $savedJavaHome = $env:JAVA_HOME
        $savedPath     = $env:PATH
        $java8Home     = $env:JAVA_HOME_8_X64
        if ($java8Home -and (Test-Path $java8Home)) {
            Write-Host "Temporarily using Java 8 (JAVA_HOME_8_X64=$java8Home) for additions install"
            Write-Host "  (avoids spotbugs-maven-plugin:3.1.6 Groovy incompatibility on JDK 17+)"
            $env:JAVA_HOME = $java8Home
            $env:PATH = (Join-Path $java8Home 'bin') + [System.IO.Path]::PathSeparator + $env:PATH
        } else {
            Write-Host "JAVA_HOME_8_X64 not set; using current JAVA_HOME: $env:JAVA_HOME"
        }

        try {
            # Extract the Maven command from mvnBuildAdditions.bat so we can append extra flags.
            $mvnCommand = Get-Content $mvnBuildScript | Where-Object { $_ -match '^mvn\s+' }
            if ($null -eq $mvnCommand) {
                throw "No mvn command found in $mvnBuildScript"
            }

            if ($IsWindows) {
                & cmd.exe /c "$mvnCommand$skipTestArgs"
            } else {
                bash -c "$mvnCommand$skipTestArgs"
            }
            if ($LASTEXITCODE -ne 0) { throw "additions maven command failed" }
        } finally {
            # Restore JAVA_HOME/PATH regardless of success or failure.
            $env:JAVA_HOME = $savedJavaHome
            $env:PATH      = $savedPath
        }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}
