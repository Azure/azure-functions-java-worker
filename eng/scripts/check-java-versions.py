#!/usr/bin/env python3
"""
Script to check for new JDK versions and update java-versions.yml
- JDK 8: From Adoptium (Eclipse Temurin)
- JDK 11+: From Microsoft OpenJDK
"""

import argparse
import json
import os
import re
import sys
import tempfile
import tarfile
import zipfile
import subprocess
from pathlib import Path
from urllib.request import urlopen, Request
from urllib.error import URLError, HTTPError


# JDK versions to check
JDK_VERSIONS = [8, 11, 17, 21, 25]

# Microsoft OpenJDK download URL patterns (JDK 11+)
LINUX_URL_TEMPLATE = "https://aka.ms/download-jdk/microsoft-jdk-{version}-linux-x64.tar.gz"
WINDOWS_URL_TEMPLATE = "https://aka.ms/download-jdk/microsoft-jdk-{version}-windows-x64.zip"

# Adoptium API for JDK 8
ADOPTIUM_API_URL = "https://api.adoptium.net/v3/info/release_versions?version=[8,9)&release_type=ga&sort_method=DATE&sort_order=DESC&page_size=1"
ADOPTIUM_DOWNLOAD_URL_TEMPLATE = "https://api.adoptium.net/v3/binary/latest/8/ga/{os}/x64/jdk/hotspot/normal/eclipse"


def get_jdk8_version_from_adoptium():
    """
    Get the latest JDK 8 version from Adoptium API.
    Returns tuple: (security_version, build_number) e.g., ('472', '08')
    """
    try:
        request = Request(ADOPTIUM_API_URL)
        request.add_header('User-Agent', 'Mozilla/5.0 (compatible; Azure-Functions-Java-Worker-Version-Checker/1.0)')
        
        with urlopen(request, timeout=30) as response:
            data = json.loads(response.read().decode('utf-8'))
            
            if 'versions' in data and len(data['versions']) > 0:
                version_info = data['versions'][0]
                security = str(version_info['security'])
                build = str(version_info['build']).zfill(2)  # Pad to 2 digits
                semver = version_info['semver']
                
                print(f"  Detected version: {semver} (security: {security}, build: {build})")
                return security, build
            else:
                print("  Warning: No version found in Adoptium API response")
                return None, None
    except (URLError, HTTPError) as e:
        print(f"  Error fetching Adoptium API: {e}")
        return None, None
    except json.JSONDecodeError as e:
        print(f"  Error parsing Adoptium API response: {e}")
        return None, None


def get_version_from_url(url):
    """
    Follow redirects and extract version number from the final URL.
    Microsoft URLs redirect to: https://download.visualstudio.microsoft.com/.../microsoft-jdk-{version}-{platform}.{ext}
    """
    try:
        request = Request(url, method='HEAD')
        with urlopen(request, timeout=30) as response:
            final_url = response.url
            print(f"  Redirect URL: {final_url}")
            
            # Extract version from URL like: microsoft-jdk-11.0.26-linux-x64.tar.gz
            # or: microsoft-jdk-21.0.6+7.1-windows-x64.zip
            pattern = r'microsoft-jdk-(\d+\.\d+\.\d+)'
            match = re.search(pattern, final_url)
            
            if match:
                return match.group(1)
            else:
                print(f"  Warning: Could not parse version from URL: {final_url}")
                return None
    except (URLError, HTTPError) as e:
        print(f"  Error fetching URL {url}: {e}")
        return None


def download_and_extract_jdk(url, os_type, extract_dir):
    """
    Download JDK archive and extract it to temporary directory.
    Returns the path to the extracted JDK directory.
    """
    print(f"  Downloading from {url}")
    
    try:
        request = Request(url)
        request.add_header('User-Agent', 'Mozilla/5.0 (compatible; Azure-Functions-Java-Worker-Version-Checker/1.0)')
        
        with urlopen(request, timeout=300) as response:
            archive_data = response.read()
        
        # Save to temporary file
        suffix = '.zip' if os_type == 'windows' else '.tar.gz'
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp_file:
            tmp_file.write(archive_data)
            archive_path = tmp_file.name
        
        print(f"  Extracting archive...")
        
        # Extract archive
        if os_type == 'windows':
            with zipfile.ZipFile(archive_path, 'r') as zip_ref:
                zip_ref.extractall(extract_dir)
        else:
            with tarfile.open(archive_path, 'r:gz') as tar_ref:
                tar_ref.extractall(extract_dir)
        
        # Clean up archive
        os.unlink(archive_path)
        
        # Find the JDK directory (usually the only subdirectory)
        extracted_items = list(Path(extract_dir).iterdir())
        if len(extracted_items) == 1 and extracted_items[0].is_dir():
            return extracted_items[0]
        else:
            # Return the extract directory itself
            return Path(extract_dir)
            
    except Exception as e:
        print(f"  Error downloading/extracting JDK: {e}")
        raise


def validate_jdk_version(jdk_path, os_type, expected_version):
    """
    Validate the JDK by running 'java -version' or 'java --version' and checking the output.
    JDK 8 uses -version, JDK 9+ uses --version (but both work with -version)
    """
    print(f"  Validating JDK installation...")
    
    # Determine java executable path
    if os_type == 'windows':
        java_exe = jdk_path / 'bin' / 'java.exe'
    else:
        java_exe = jdk_path / 'bin' / 'java'
    
    if not java_exe.exists():
        raise FileNotFoundError(f"Java executable not found at {java_exe}")
    
    try:
        # Run java -version (works for all JDK versions)
        # Note: -version outputs to stderr, not stdout
        result = subprocess.run(
            [str(java_exe), '-version'],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        # Check both stdout and stderr as -version outputs to stderr
        output = result.stdout + result.stderr
        print(f"  Java version output:\n{output}")
        
        # Check if expected version is in the output
        if expected_version in output:
            print(f"  [OK] Validation successful: Found version {expected_version}")
            return True
        else:
            print(f"  [FAIL] Validation failed: Expected version {expected_version} not found in output")
            return False
            
    except subprocess.TimeoutExpired:
        print(f"  [FAIL] Validation failed: java --version command timed out")
        return False
    except Exception as e:
        print(f"  [FAIL] Validation failed: {e}")
        return False


def check_jdk_version(jdk_version, os_type):
    """
    Check the latest version for a specific JDK major version and OS.
    Downloads, validates, and returns the version string.
    For JDK 8, returns tuple: (security_version, build_number)
    For JDK 11+, returns version string
    """
    print(f"\nChecking JDK {jdk_version} for {os_type}...")
    
    # Special handling for JDK 8 (from Adoptium)
    if jdk_version == 8:
        # Get version info from API
        security, build = get_jdk8_version_from_adoptium()
        
        if not security or not build:
            print(f"  Failed to detect version for JDK 8")
            return None
        
        # Determine download URL
        adoptium_os = 'linux' if os_type == 'linux' else 'windows'
        url = ADOPTIUM_DOWNLOAD_URL_TEMPLATE.format(os=adoptium_os)
        
        # For validation, construct the expected version string
        # Adoptium uses format like "1.8.0_472"
        expected_version = f"1.8.0_{security}"
        
        # Download and validate
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                jdk_path = download_and_extract_jdk(url, os_type, tmp_dir)
                
                if validate_jdk_version(jdk_path, os_type, expected_version):
                    return (security, build)
                else:
                    print(f"  Validation failed for JDK 8 {os_type} version {expected_version}")
                    sys.exit(1)
                    
            except Exception as e:
                print(f"  Error during download/validation: {e}")
                sys.exit(1)
    
    # Microsoft OpenJDK handling (JDK 11+)
    else:
        # Determine URL template
        if os_type == 'linux':
            url = LINUX_URL_TEMPLATE.format(version=jdk_version)
        else:
            url = WINDOWS_URL_TEMPLATE.format(version=jdk_version)
        
        # Get version from URL redirect
        version = get_version_from_url(url)
        
        if not version:
            print(f"  Failed to detect version for JDK {jdk_version} on {os_type}")
            return None
        
        print(f"  Detected version: {version}")
        
        # Download and validate
        with tempfile.TemporaryDirectory() as tmp_dir:
            try:
                jdk_path = download_and_extract_jdk(url, os_type, tmp_dir)
                
                if validate_jdk_version(jdk_path, os_type, version):
                    return version
                else:
                    print(f"  Validation failed for JDK {jdk_version} {os_type} version {version}")
                    sys.exit(1)
                    
            except Exception as e:
                print(f"  Error during download/validation: {e}")
                sys.exit(1)


def load_current_versions(yaml_file):
    """
    Load current versions from java-versions.yml using regex (no YAML library needed)
    """
    with open(yaml_file, 'r') as f:
        content = f.read()
    
    versions = {}
    # Pattern to match version variables like: JDK11_LINUX_VERSION: '11.0.26'
    version_pattern = r"(JDK\d+_(?:LINUX|WINDOWS)_VERSION):\s*'([^']+)'"
    
    for match in re.finditer(version_pattern, content):
        var_name = match.group(1)
        value = match.group(2)
        versions[var_name] = value
    
    # Pattern to match build variables like: JDK8_LINUX_BUILD: '06'
    build_pattern = r"(JDK8_(?:LINUX|WINDOWS)_BUILD):\s*'([^']+)'"
    
    for match in re.finditer(build_pattern, content):
        var_name = match.group(1)
        value = match.group(2)
        versions[var_name] = value
    
    return versions


def update_yaml_file(yaml_file, new_versions):
    """
    Update the java-versions.yml file with new versions.
    Preserves formatting and comments.
    """
    print(f"\nUpdating {yaml_file}...")
    
    with open(yaml_file, 'r') as f:
        content = f.read()
    
    # Update each version variable
    for var_name, new_value in new_versions.items():
        # Pattern to match the variable line
        pattern = rf"({var_name}:\s*')[^']*(')"
        replacement = rf"\g<1>{new_value}\g<2>"
        
        content = re.sub(pattern, replacement, content)
        print(f"  Updated {var_name} to '{new_value}'")
    
    with open(yaml_file, 'w') as f:
        f.write(content)
    
    print("[OK] YAML file updated successfully")


def main():
    """
    Main function to check all JDK versions and update the YAML file if needed.
    """
    parser = argparse.ArgumentParser(description='Check Microsoft OpenJDK versions')
    parser.add_argument('--os', choices=['linux', 'windows'], required=True,
                        help='Operating system to check (linux or windows)')
    args = parser.parse_args()
    
    os_type = args.os
    
    print("=" * 80)
    print(f"Microsoft OpenJDK Version Checker - {os_type.upper()}")
    print("=" * 80)
    
    # Determine path to java-versions.yml
    script_dir = Path(__file__).parent
    repo_root = script_dir.parent.parent
    yaml_file = repo_root / 'eng' / 'ci' / 'templates' / 'java-versions.yml'
    
    if not yaml_file.exists():
        print(f"Error: Could not find {yaml_file}")
        sys.exit(1)
    
    print(f"\nYAML file: {yaml_file}")
    
    # Load current versions
    current_versions = load_current_versions(yaml_file)
    print(f"\nCurrent {os_type} versions:")
    for key, value in sorted(current_versions.items()):
        if os_type.upper() in key:
            print(f"  {key}: {value}")
    
    # Check all JDK versions for this OS only
    detected_versions = {}
    
    for jdk_version in JDK_VERSIONS:
        version = check_jdk_version(jdk_version, os_type)
        if version:
            # JDK 8 returns tuple (security, build), others return version string
            if jdk_version == 8:
                security, build = version
                version_var = f"JDK8_{os_type.upper()}_VERSION"
                build_var = f"JDK8_{os_type.upper()}_BUILD"
                detected_versions[version_var] = security
                detected_versions[build_var] = build
            else:
                var_name = f"JDK{jdk_version}_{os_type.upper()}_VERSION"
                detected_versions[var_name] = version
    
    # Compare and determine if update is needed
    print("\n" + "=" * 80)
    print("Version Comparison")
    print("=" * 80)
    
    updates_needed = {}
    changes_detected = False
    
    for var_name, new_version in sorted(detected_versions.items()):
        current_version = current_versions.get(var_name, 'N/A')
        
        if current_version != new_version:
            print(f"[UPDATE] {var_name}: {current_version} -> {new_version}")
            updates_needed[var_name] = new_version
            changes_detected = True
        else:
            print(f"[OK] {var_name}: {current_version} (up to date)")
    
    # Update YAML file if needed
    if changes_detected:
        print("\n" + "=" * 80)
        print("Updates Required")
        print("=" * 80)
        update_yaml_file(yaml_file, updates_needed)
        print(f"\n[OK] Version check complete - {os_type} updates applied")
        sys.exit(0)
    else:
        print(f"\n[OK] All {os_type} versions are up to date - no changes needed")
        sys.exit(0)


if __name__ == '__main__':
    main()
