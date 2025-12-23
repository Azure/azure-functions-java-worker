#!/usr/bin/env python3
"""
Script to check for new Microsoft OpenJDK versions and update java-versions.yml
"""

import argparse
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
import yaml


# JDK versions to check (excluding JDK 8 as it's not from Microsoft)
JDK_VERSIONS = [11, 17, 21, 25]

# Microsoft OpenJDK download URL patterns
LINUX_URL_TEMPLATE = "https://aka.ms/download-jdk/microsoft-jdk-{version}-linux-x64.tar.gz"
WINDOWS_URL_TEMPLATE = "https://aka.ms/download-jdk/microsoft-jdk-{version}-windows-x64.zip"


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
        with urlopen(url, timeout=300) as response:
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
    Validate the JDK by running 'java --version' and checking the output.
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
        # Run java --version
        result = subprocess.run(
            [str(java_exe), '--version'],
            capture_output=True,
            text=True,
            timeout=10
        )
        
        print(f"  Java version output:\n{result.stdout}")
        
        # Check if expected version is in the output
        if expected_version in result.stdout:
            print(f"  ✓ Validation successful: Found version {expected_version}")
            return True
        else:
            print(f"  ✗ Validation failed: Expected version {expected_version} not found in output")
            return False
            
    except subprocess.TimeoutExpired:
        print(f"  ✗ Validation failed: java --version command timed out")
        return False
    except Exception as e:
        print(f"  ✗ Validation failed: {e}")
        return False


def check_jdk_version(jdk_version, os_type):
    """
    Check the latest version for a specific JDK major version and OS.
    Downloads, validates, and returns the version string.
    """
    print(f"\nChecking JDK {jdk_version} for {os_type}...")
    
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
    Load current versions from java-versions.yml
    """
    with open(yaml_file, 'r') as f:
        data = yaml.safe_load(f)
    return data.get('variables', {})


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
    
    print("✓ YAML file updated successfully")


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
        if 'JDK8' not in key and os_type.upper() in key:
            print(f"  {key}: {value}")
    
    # Check all JDK versions for this OS only
    detected_versions = {}
    
    for jdk_version in JDK_VERSIONS:
        version = check_jdk_version(jdk_version, os_type)
        if version:
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
            print(f"🔄 {var_name}: {current_version} -> {new_version}")
            updates_needed[var_name] = new_version
            changes_detected = True
        else:
            print(f"✓ {var_name}: {current_version} (up to date)")
    
    # Update YAML file if needed
    if changes_detected:
        print("\n" + "=" * 80)
        print("Updates Required")
        print("=" * 80)
        update_yaml_file(yaml_file, updates_needed)
        print(f"\n✓ Version check complete - {os_type} updates applied")
        sys.exit(0)
    else:
        print(f"\n✓ All {os_type} versions are up to date - no changes needed")
        sys.exit(0)


if __name__ == '__main__':
    main()
