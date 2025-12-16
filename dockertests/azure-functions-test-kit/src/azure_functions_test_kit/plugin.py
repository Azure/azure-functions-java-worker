# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Pytest plugin for Azure Functions Test Kit.

Provides automatic .env file loading and optional helper fixtures.
Tests are free to create their own fixtures using the exported classes.
"""
import os
from pathlib import Path
from dotenv import load_dotenv


def pytest_configure(config):
    """Load .env file if it exists in the test directory."""
    # Try to find .env file in the test directory or parent directories
    test_dir = Path.cwd()
    env_file = test_dir / '.env'
    
    if env_file.exists():
        load_dotenv(env_file)
        print(f"✅ [azure-functions-test-kit] Loaded configuration from {env_file}")
    else:
        # Check parent directories (up to 3 levels)
        for parent in test_dir.parents[:3]:
            env_file = parent / '.env'
            if env_file.exists():
                load_dotenv(env_file)
                print(f"✅ [azure-functions-test-kit] Loaded configuration from {env_file}")
                break
        else:
            print("ℹ️  [azure-functions-test-kit] No .env file found, using environment variables")
