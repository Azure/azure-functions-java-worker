function RunTest([string] $project, [string] $description,[bool] $skipBuild = $false, $filter = $null) {
    Write-Host "Running test: $description" -ForegroundColor DarkCyan
    Write-Host "-----------------------------------------------------------------------------" -ForegroundColor DarkCyan
    Write-Host

    $cmdargs = "test", "$project", "-v", "q"
    
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


$tests = @(
  @{project ="endtoendtests\Azure.Functions.Java.Tests.E2E\Azure.Functions.Java.Tests.E2E\Azure.Functions.Java.Tests.E2E.csproj"; description="E2E integration tests"}
)

$success = $true
$testRunSucceeded = $true


Write-Host "Deleting Functions Core Tools if exists...."
Remove-Item -Force ./Azure.Functions.Cli.zip -ErrorAction Ignore
Remove-Item -Recurse -Force ./Azure.Functions.Cli -ErrorAction Ignore

Write-Host "Downloading Functions Core Tools...."

$url = "https://functionsclibuilds.blob.core.windows.net/builds/2/latest/Azure.Functions.Cli.win-x86.zip"
$output = "$PSScriptRoot\Azure.Functions.Cli.zip"
$start_time = Get-Date

$wc = New-Object System.Net.WebClient
$wc.DownloadFile($url, $output)

Write-Host "Extracting Functions Core Tools...."
Expand-Archive "$PSScriptRoot\Azure.Functions.Cli.zip" -DestinationPath "$PSScriptRoot\Azure.Functions.Cli"


Write-Host "Copying azure-functions-java-worker to  Functions CLI workers directory...."
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName ".\Azure.Functions.Cli\workers\java\azure-functions-java-worker.jar" }


Write-Host "Staring Functions Host...."

$Env:AzureWebJobsScriptRoot = "$PSScriptRoot\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests"
$Env:AZURE_FUNCTIONS_ENVIRONMENT = "development"
$Env:Path = $Env:Path+";$PSScriptRoot\Azure.Functions.Cli"

$Env:Path = $Env:Path+";$PSScriptRoot\Azure.Functions.Cli"

Write-Host "Building azure-functions-java-worker\endtoendtests...."

Write-Host "Building azure-functions-java-endtoendtests"
Push-Location -Path .\endtoendtests
mvn clean package 
StopOnFailedExecution
Pop-Location

Write-Host "Copying EventHubs function.json as temporary workaround...."
Copy-Item "$PSScriptRoot\endtoendtests\functionInputString.json" "$PSScriptRoot\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests\EventHubTriggerAndOutputJSON"
Copy-Item "$PSScriptRoot\endtoendtests\functionInputJson.json" "$PSScriptRoot\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests\EventHubTriggerAndOutputString"

$proc = start-process -filepath func.exe -WorkingDirectory "$PSScriptRoot\endtoendtests\target\azure-functions\azure-functions-java-endtoendtests" -ArgumentList "host start" -PassThru
# wait for host to start
Start-Sleep -s 30


foreach ($test in $tests){
    $testRunSucceeded = RunTest $test.project $test.description $testRunSucceeded $test.filter
    $success = $testRunSucceeded -and $success
}

Write-Host "Stopping Functions Host...."
Stop-Process -Id $proc.Id -ErrorAction Ignore -Force

if (-not $success) { exit 1 }

