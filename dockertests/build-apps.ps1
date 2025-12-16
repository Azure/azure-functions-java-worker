#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Build and package Azure Functions Java apps into squashfs packages.

.DESCRIPTION
    This script iterates through each directory in app-src, runs mvn clean package,
    and creates a squashfs package from the target/azure-functions/* contents.
    The packages are placed in the apps directory.

.NOTES
    Requires:
    - Maven (mvn)
    - Docker (for creating squashfs packages)
#>

param(
    [string]$AppSrcDir = "app-src",
    [string]$AppsDir = "app-packages"
)

# Get script directory
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppSrcPath = Join-Path $ScriptDir $AppSrcDir
$AppsPath = Join-Path $ScriptDir $AppsDir

# Ensure apps directory exists
if (-not (Test-Path $AppsPath)) {
    Write-Host "[INFO] Creating apps directory: $AppsPath" -ForegroundColor Cyan
    New-Item -ItemType Directory -Path $AppsPath | Out-Null
}

# Check if Docker is available
$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) {
    Write-Host "[ERROR] Docker not found. Please install Docker Desktop." -ForegroundColor Red
    Write-Host "   Download from: https://www.docker.com/products/docker-desktop" -ForegroundColor Yellow
    exit 1
}

# Verify Docker is running
docker ps 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker is not running. Please start Docker Desktop." -ForegroundColor Red
    exit 1
}

# Get all directories in app-src
$appDirs = Get-ChildItem -Path $AppSrcPath -Directory

if ($appDirs.Count -eq 0) {
    Write-Host "⚠️  No application directories found in $AppSrcPath" -ForegroundColor Yellow
    exit 0
}

Write-Host "[INFO] Building and packaging $($appDirs.Count) application(s)..." -ForegroundColor Green
Write-Host "[INFO] Using Docker to create squashfs packages" -ForegroundColor Cyan
Write-Host ""

$successCount = 0
$failCount = 0

foreach ($appDir in $appDirs) {
    $appName = $appDir.Name
    $appPath = $appDir.FullName
    $targetAzureFunctionsPath = Join-Path $appPath "target\azure-functions"
    $packagePath = Join-Path $AppsPath "$appName.squashfs"
    
    Write-Host "========================================================================" -ForegroundColor Cyan
    Write-Host "[PACKAGE] Processing: $appName" -ForegroundColor Cyan
    Write-Host "========================================================================" -ForegroundColor Cyan
    
    try {
        # Step 1: Run mvn clean package
        Write-Host "[BUILD] Building with Maven..." -ForegroundColor Yellow
        Push-Location $appPath
        
        $mavenOutput = mvn clean package 2>&1
        $mavenExitCode = $LASTEXITCODE
        
        Pop-Location
        
        if ($mavenExitCode -ne 0) {
            Write-Host "[ERROR] Maven build failed for $appName" -ForegroundColor Red
            Write-Host $mavenOutput -ForegroundColor DarkGray
            $failCount++
            continue
        }
        
        Write-Host "[OK] Maven build successful" -ForegroundColor Green
        
        # Step 2: Check if target/azure-functions directory exists
        if (-not (Test-Path $targetAzureFunctionsPath)) {
            Write-Host "[ERROR] target/azure-functions directory not found at $targetAzureFunctionsPath" -ForegroundColor Red
            $failCount++
            continue
        }
        
        # Find the actual function app directory inside target/azure-functions
        $functionAppDirs = Get-ChildItem -Path $targetAzureFunctionsPath -Directory
        
        if ($functionAppDirs.Count -eq 0) {
            Write-Host "[ERROR] No function app directory found in $targetAzureFunctionsPath" -ForegroundColor Red
            $failCount++
            continue
        }
        
        # Use the first (should be only) directory
        $functionAppPath = $functionAppDirs[0].FullName
        Write-Host "[INFO] Function app directory: $($functionAppDirs[0].Name)" -ForegroundColor Gray
        
        # Step 3: Create squashfs package using Docker
        Write-Host "[PACKAGE] Creating squashfs package using Docker..." -ForegroundColor Yellow
        
        # Remove old package if exists
        if (Test-Path $packagePath) {
            Remove-Item $packagePath -Force
            Write-Host "[INFO] Removed existing package" -ForegroundColor Gray
        }
        
        # Get the parent directory for the package (for volume mount)
        $packageDir = Split-Path -Parent $packagePath
        $packageFileName = Split-Path -Leaf $packagePath
        
        Write-Host "[INFO] Source: $functionAppPath" -ForegroundColor Gray
        Write-Host "[INFO] Output: $packagePath" -ForegroundColor Gray
        
        # Create squashfs package using Docker
        # Use Ubuntu image and install squashfs-tools on the fly
        # Mount both source and destination directories
        $dockerArgs = @(
            "run"
            "--rm"
            "-v"
            "${functionAppPath}:/source"
            "-v"
            "${packageDir}:/output"
            "ubuntu:22.04"
            "bash"
            "-c"
            "apt-get update -qq && apt-get install -y -qq squashfs-tools > /dev/null 2>&1 && mksquashfs /source /output/$packageFileName -noappend -comp gzip"
        )
        
        Write-Host "[INFO] Running Docker container to create squashfs package..." -ForegroundColor DarkGray
        
        $dockerOutput = & docker @dockerArgs 2>&1
        $dockerExitCode = $LASTEXITCODE
        
        if ($dockerExitCode -ne 0) {
            Write-Host "[ERROR] Failed to create squashfs package for $appName" -ForegroundColor Red
            Write-Host $dockerOutput -ForegroundColor DarkGray
            $failCount++
            continue
        }
        
        # Verify package was created and get size
        if (-not (Test-Path $packagePath)) {
            Write-Host "[ERROR] Package file not created at $packagePath" -ForegroundColor Red
            $failCount++
            continue
        }
        
        $packageSize = (Get-Item $packagePath).Length
        $packageSizeMB = [math]::Round($packageSize / 1MB, 2)
        
        Write-Host "[OK] Package created: $appName.squashfs ($packageSizeMB MB)" -ForegroundColor Green
        $successCount++
        
    }
    catch {
        Write-Host "[ERROR] Error processing $appName : $_" -ForegroundColor Red
        $failCount++
    }
    
    Write-Host ""
}

# Summary
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "[SUMMARY] Build Summary" -ForegroundColor Cyan
Write-Host "========================================================================" -ForegroundColor Cyan
Write-Host "[OK] Successful: $successCount" -ForegroundColor Green
Write-Host "[ERROR] Failed: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Gray" })
Write-Host ""

if ($successCount -gt 0) {
    Write-Host "[INFO] Packages available in: $AppsPath" -ForegroundColor Cyan
}

exit $failCount
