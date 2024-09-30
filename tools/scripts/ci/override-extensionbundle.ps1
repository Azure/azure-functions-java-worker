param(
    [string]$hostJsonPath = "./endtoendtests/Azure.Functions.Java.Tests.E2E/host.json",
    [bool]$isPreviewBundle = $false
)

$hostJson = Get-Content $hostJsonPath -Raw | ConvertFrom-Json

# Ensure the 'extensions' and 'extensionBundle' properties exist
if (-not $hostJson.extensions) {
    $hostJson | Add-Member -MemberType NoteProperty -Name extensions -Value @{}
}

if (-not $hostJson.extensions.extensionBundle) {
    $hostJson.extensions | Add-Member -MemberType NoteProperty -Name extensionBundle -Value @{}
}

# Preserve the existing 'version', or set a default if not present
$version = $hostJson.extensions.extensionBundle.version
if (-not $version) {
    $version = "1.9.0"
}

# Set the 'id' based on the value of 'isPreviewBundle'
if ($isPreviewBundle) {
    $hostJson.extensions.extensionBundle.id = "Microsoft.Azure.Functions.ExtensionBundle.Preview"
} else {
    $hostJson.extensions.extensionBundle.id = "Microsoft.Azure.Functions.ExtensionBundle"
}

# Assign the preserved or default 'version'
$hostJson.extensions.extensionBundle.version = $version

$hostJson | ConvertTo-Json -Depth 100 | Set-Content $hostJsonPath
