# Variables for first repository
$repoUrl1 = 'https://github.com/Azure/azure-functions-java-additions.git'
$branchName1 = 'dev'
$repoName1 = 'azure-functions-java-additions'

# Clone the first repository
git clone $repoUrl1

# Change directory to the cloned repository
Set-Location $repoName1

# Checkout the desired branch
git checkout $branchName1

# Detect OS and execute build accordingly
if ($IsWindows) {
    # Run the batch script (mvnBuild.bat)
    & "..\mvnBuildAdditions.bat"
} else {
    # Extract and explicitly invoke the mvn command from mvnBuild.bat
    $mvnCommand = Get-Content "../mvnBuildAdditions.bat" | Where-Object { $_ -match '^mvn\s+' }
    if ($null -ne $mvnCommand) {
        # Execute the extracted mvn command explicitly as a single line
        bash -c "$mvnCommand"
    } else {
        Write-Error "No mvn command found in mvnBuild.bat."
    }
}
