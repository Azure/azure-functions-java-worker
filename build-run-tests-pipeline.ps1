# A function that checks exit codes and fails script if an error is found
function StopOnFailedExecution {
    if ($LastExitCode)
    {
        exit $LastExitCode
    }
}

function RunTest([string] $project, [string] $description,[bool] $skipBuild = $false, $filter = $null) {
    Write-Host "Running test: $description" -ForegroundColor DarkCyan
    Write-Host "-----------------------------------------------------------------------------" -ForegroundColor DarkCyan
    Write-Host

    $cmdargs = "test", "$project", "-v", "q", "-l", "trx", "-r",".\testResults"
    
    if ($filter) {
       $cmdargs += "--filter", "$filter"
    }

    & dotnet $cmdargs | Out-Host
    $r = $?
    
    Write-Host
    Write-Host "-----------------------------------------------------------------------------" -ForegroundColor DarkCyan
    Write-Host

    return $r
}

$currDir =  Get-Location

Write-Host "Building endtoendtests...."
$Env:Path = $Env:Path+";$currDir\Azure.Functions.Cli"
Push-Location -Path "./endtoendtests" -StackName javaWorkerDir
Write-Host "Building azure-functions-maven-com.microsoft.azure.functions.endtoendtests"
cmd.exe /c '.\..\mvnBuildSkipTests.bat'
StopOnFailedExecution
Pop-Location -StackName "javaWorkerDir"

$tests = @(
  @{project ="endtoendtests\Azure.Functions.Java.Tests.E2E\Azure.Functions.Java.Tests.E2E\Azure.Functions.Java.Tests.E2E.csproj"; description="E2E integration tests"}
)

$success = $true
$testRunSucceeded = $true

foreach ($test in $tests){
    $testRunSucceeded = RunTest $test.project $test.description $testRunSucceeded $test.filter
    $success = $testRunSucceeded -and $success
}

if (-not $success) { exit 1 }

