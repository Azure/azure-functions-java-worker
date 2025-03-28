# Variables
$repoUrl = 'https://github.com/ahmedmuhsin/azure-functions-java-additions.git'
$branchName = 'sdk-types'
$repoName = 'azure-functions-java-additions'

# Clone the repository
git clone $repoUrl

# Change directory to the cloned repository
Set-Location $repoName

# Checkout the desired branch
git checkout $branchName

# Detect OS and execute accordingly
if ($IsWindows) {
    # Run the batch script (mvnBuild.bat)
    & ".\mvnBuild.bat"
} else {
    # Extract and explicitly invoke the mvn command from mvnBuild.bat
    $mvnCommand = Get-Content "./mvnBuild.bat" | Where-Object { $_ -match '^mvn\s+' }
    if ($null -ne $mvnCommand) {
        # Execute the extracted mvn command explicitly as a single line
        bash -c "$mvnCommand"
    } else {
        Write-Error "No mvn command found in mvnBuild.bat."
    }
}
