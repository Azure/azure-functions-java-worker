
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


Write-Host "Deleting Functions Host Binaries...."
Remove-Item -Force ./Functions.Binaries.zip
Remove-Item -Recurse -Force ./Functions.Binaries

Write-Host "Downloading Functions Host...."

$url = "https://ci.appveyor.com/api/buildjobs/5tki14blq24k9mgm/artifacts/Functions.Binaries.2.0.12118.no-runtime.zip"
$output = "$PSScriptRoot\Functions.Binaries.zip"
$start_time = Get-Date


$wc = New-Object System.Net.WebClient
$wc.DownloadFile($url, $output)

Write-Output "Time taken: $((Get-Date).Subtract($start_time).Seconds) second(s)"

Write-Host "Extracting Functions Host...."
Expand-Archive "./Functions.Binaries.zip" -DestinationPath "./Functions.Binaries"


Write-Host "Copying azure-functions-java-worker to  Functions Host workers directory...."
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName "./Functions.Binaries/workers/java/azure-functions-java-worker.jar" }


Write-Host "Staring Functions Host...."

$Env:AzureWebJobsScriptRoot = "$PSScriptRoot/endtoendtests/target/azure-functions/azure-functions-java-endtoendtests"
$Env:FUNCTIONS_WORKER_RUNTIME = "java"
$Env:AZURE_FUNCTIONS_ENVIRONMENT = "development"

Write-Host $Env:AzureWebJobsScriptRoot

$proc = start-process -filepath dotnet -ArgumentList "./Functions.Binaries/Microsoft.Azure.WebJobs.Script.WebHost.dll" -PassThru
# wait for host to start
Start-Sleep -s 10


foreach ($test in $tests){
    $testRunSucceeded = RunTest $test.project $test.description $testRunSucceeded $test.filter
    $success = $testRunSucceeded -and $success
}

Write-Host "Stopping Functions Host...."
Start-Sleep -s 5
$proc.Kill()

if (-not $success) { exit 1 }

