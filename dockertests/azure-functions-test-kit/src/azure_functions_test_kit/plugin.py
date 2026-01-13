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
import pytest


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


@pytest.hookimpl(tryfirst=True, hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """Hook to capture test failures and display container logs."""
    # Execute all other hooks to obtain the report object
    outcome = yield
    rep = outcome.get_result()
    
    # Only process test failures in the call phase (actual test execution)
    if rep.when == "call" and rep.failed:
        # Try to get the test_env fixture from the test
        test_env = None
        if hasattr(item, 'funcargs') and 'test_env' in item.funcargs:
            test_env = item.funcargs['test_env']
        
        if test_env and hasattr(test_env, 'functions_controller'):
            functions_controller = test_env.functions_controller
            if functions_controller:
                try:
                    logs = functions_controller.get_container_logs()
                    
                    # Add container logs to the test report
                    print("\n" + "="*80)
                    print("🐳 CONTAINER LOGS FOR FAILED TEST")
                    print("="*80)
                    print(logs)
                    print("="*80 + "\n")
                    
                    # Also append to the test's longrepr for visibility in reports
                    if hasattr(rep, 'longrepr'):
                        log_section = f"\n\n{'='*80}\n🐳 CONTAINER LOGS\n{'='*80}\n{logs}\n{'='*80}\n"
                        if rep.longrepr:
                            rep.longrepr = str(rep.longrepr) + log_section
                        else:
                            rep.longrepr = log_section
                            
                except Exception as e:
                    print(f"\n⚠️  Failed to retrieve container logs: {e}\n")
