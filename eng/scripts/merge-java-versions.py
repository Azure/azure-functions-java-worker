#!/usr/bin/env python3
"""
Script to merge Linux and Windows Java version updates into a single YAML file.
"""

import argparse
import sys
import re
from pathlib import Path


def load_yaml_with_formatting(file_path):
    """
    Load YAML file and preserve its content as a string for formatting.
    Uses regex instead of yaml library.
    """
    with open(file_path, 'r') as f:
        content = f.read()
    
    # Extract variables using regex
    variables = {}
    pattern = r"(JDK\d+_(?:LINUX|WINDOWS)_(?:VERSION|BUILD)):\s*'([^']+)'"
    
    for match in re.finditer(pattern, content):
        var_name = match.group(1)
        value = match.group(2)
        variables[var_name] = value
    
    return content, variables


def merge_versions(linux_data, windows_data):
    """
    Merge Linux and Windows version data.
    """
    merged = {}
    
    # Merge all unique keys
    all_keys = set(linux_data.keys()) | set(windows_data.keys())
    
    for key in sorted(all_keys):
        # Skip JDK8 as it's not managed by Microsoft
        if 'JDK8' in key:
            merged[key] = linux_data.get(key, windows_data.get(key))
            continue
        
        # Use the appropriate source based on OS in the key name
        if 'LINUX' in key:
            merged[key] = linux_data.get(key)
        elif 'WINDOWS' in key:
            merged[key] = windows_data.get(key)
        else:
            # Fallback: use whichever source has it
            merged[key] = linux_data.get(key, windows_data.get(key))
    
    return merged


def update_yaml_file(output_file, merged_versions):
    """
    Update the output YAML file with merged versions.
    Preserves formatting and comments.
    """
    import re
    
    # Read current content
    with open(output_file, 'r') as f:
        content = f.read()
    
    # Update each version variable
    for var_name, new_value in merged_versions.items():
        if new_value is not None:  # Only update if we have a value
            # Pattern to match the variable line
            pattern = rf"({var_name}:\s*')[^']*(')"
            replacement = rf"\g<1>{new_value}\g<2>"
            
            new_content = re.sub(pattern, replacement, content)
            
            if new_content != content:
                print(f"  Updated {var_name} to '{new_value}'")
                content = new_content
    
    # Write updated content
    with open(output_file, 'w') as f:
        f.write(content)


def main():
    """
    Main function to merge Linux and Windows version updates.
    """
    parser = argparse.ArgumentParser(description='Merge Linux and Windows Java version updates')
    parser.add_argument('--linux', required=True, help='Path to Linux updated java-versions.yml')
    parser.add_argument('--windows', required=True, help='Path to Windows updated java-versions.yml')
    parser.add_argument('--output', required=True, help='Path to output java-versions.yml')
    args = parser.parse_args()
    
    print("=" * 80)
    print("Merging Java Version Updates")
    print("=" * 80)
    
    # Load both files
    print(f"\nLoading Linux versions from: {args.linux}")
    _, linux_data = load_yaml_with_formatting(args.linux)
    
    print(f"Loading Windows versions from: {args.windows}")
    _, windows_data = load_yaml_with_formatting(args.windows)
    
    # Merge versions
    print("\nMerging versions...")
    merged_versions = merge_versions(linux_data, windows_data)
    
    # Update output file
    print(f"\nUpdating output file: {args.output}")
    update_yaml_file(args.output, merged_versions)
    
    print("\n✓ Merge complete")


if __name__ == '__main__':
    main()
