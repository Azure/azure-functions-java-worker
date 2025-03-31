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
