param (
  [string]$buildNumber = $env:APPVEYOR_BUILD_NUMBER
)

Write-Host "buildNumber: " $buildNumber
mvn clean install -DskipTests
remove-item pkg -Recurse -ErrorAction Ignore
mkdir pkg
Get-ChildItem -Path .\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName .\pkg\azure-functions-java-worker.jar }
copy-item ./worker.config.json pkg
copy-item ./tools/AzureFunctionsJavaWorker.nuspec pkg/
set-location pkg
nuget pack -Properties version=$buildNumber
set-location ..