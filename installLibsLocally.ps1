# Variables for first repository
$repoUrl1 = 'https://github.com/ahmedmuhsin/azure-functions-java-additions.git'
$branchName1 = 'sdk-types'
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
    & ".\mvnBuild.bat"
} else {
    bash -c 'mvn clean install -U -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B -Dgpg.skip -Dspotbugs.skip=true'
}

# Return to the parent directory before working on the second repository
Set-Location ..

# Variables for second repository
$repoUrl2 = 'https://github.com/ahmedmuhsin/azure-maven-plugins.git'
$branchName2 = 'sdk-types'
$repoName2 = 'azure-maven-plugins'

# Clone the second repository
git clone $repoUrl2

# Change directory to the cloned repository
Set-Location $repoName2

# Checkout the desired branch
git checkout $branchName2

# Run Maven command to build/install, skipping tests and javadoc
if ($IsWindows) {
    & "mvn" "clean" "install" "-DskipTests" "-Dmaven.javadoc.skip=true"
} else {
    bash -c "mvn clean install -DskipTests -Dmaven.javadoc.skip=true"
}
