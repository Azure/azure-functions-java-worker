[CmdletBinding()]
param(
    [string]$AdditionsRepoUrl = 'https://github.com/Azure/azure-functions-java-additions.git',
    [string]$AdditionsBranch  = 'dev'
)

$ErrorActionPreference = 'Stop'

$repoName       = 'azure-functions-java-additions'
$workerRoot     = $PSScriptRoot
$cloneDir       = Join-Path $workerRoot $repoName
$mvnBuildScript = Join-Path $workerRoot 'mvnBuildAdditions.bat'

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
        if ($IsWindows) {
            # Run the batch script (mvnBuildAdditions.bat)
            & $mvnBuildScript
            if ($LASTEXITCODE -ne 0) { throw "mvnBuildAdditions.bat failed" }
        } else {
            # Extract and explicitly invoke the mvn command from mvnBuildAdditions.bat
            $mvnCommand = Get-Content $mvnBuildScript | Where-Object { $_ -match '^mvn\s+' }
            if ($null -ne $mvnCommand) {
                # Execute the extracted mvn command explicitly as a single line
                bash -c "$mvnCommand"
                if ($LASTEXITCODE -ne 0) { throw "mvn command failed" }
            } else {
                throw "No mvn command found in $mvnBuildScript"
            }
        }
    } finally {
        Pop-Location
    }
} finally {
    Pop-Location
}
