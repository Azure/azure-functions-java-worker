#!/usr/bin/env python3
"""
Pytest tests for SDK types flag behavior in Azure Functions Java worker.
Tests verify that the JAVA_ENABLE_SDK_TYPES environment variable is correctly
processed during both WorkerInit and FunctionEnvironmentReload.
"""

import pytest
import time
from pathlib import Path
from dotenv import load_dotenv
from utils import FunctionsTestEnvironment


# Configuration
APP_NAME = "BlobSdkType"

# Expected log line patterns
EXPECTED_WORKERINIT_TRUE_NULL = "Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : null)"
EXPECTED_RELOAD_FALSE = "Initialized SDK types enabled flag: false (from 'JAVA_ENABLE_SDK_TYPES' environment variable : 'false')"
EXPECTED_RELOAD_TRUE = "Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : 'true')"
EXPECTED_RELOAD_TRUE_NULL = "Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : null)"


# Load environment variables from .env file
@pytest.fixture(scope="session", autouse=True)
def load_env():
    """Load .env file before running tests"""
    env_file = Path(__file__).parent / '.env'
    if env_file.exists():
        load_dotenv(env_file)
        print(f"✅ Loaded configuration from {env_file}")
    else:
        print(f"⚠️  No .env file found at {env_file}, using defaults")


@pytest.fixture
def test_env():
    """Create a fresh test environment for each test"""
    with FunctionsTestEnvironment(apps_to_upload=[APP_NAME]) as env:
        yield env


def verify_sdk_types_logs(test_env, sdk_types_value, expected_first_line, expected_second_line):
    """
    Helper function to verify SDK types flag behavior.
    
    Args:
        test_env: TestEnvironment fixture
        sdk_types_value: Value for JAVA_ENABLE_SDK_TYPES env var (None to omit)
        expected_first_line: Expected first log line (WorkerInit)
        expected_second_line: Expected second log line (FunctionEnvironmentReload)
    """
    # Get app URL and build environment variables
    app_url = test_env.get_blob_sas_url(APP_NAME)
    env_vars = {
        'SCM_RUN_FROM_PACKAGE': app_url,
        'AzureWebJobsStorage': test_env.docker_storage_connection_string
    }
    
    # Add JAVA_ENABLE_SDK_TYPES if specified
    if sdk_types_value is not None:
        env_vars['JAVA_ENABLE_SDK_TYPES'] = sdk_types_value
    
    # Assign container with environment variables
    test_env.functions_controller.assign_container(env=env_vars)
    
    # Wait for functions to be loaded
    assert test_env.functions_controller.wait_for_host_running(timeout=360), \
        "Functions host did not reach running state within timeout"

    assert test_env.functions_controller.wait_for_functions_loaded(timeout=360), \
        "Functions did not load within timeout"
    
    # Get container logs and search for SDK types initialization messages
    # Retry logic: Some Java versions have delay between function load and log appearance
    matches = []
    max_retries = 50
    retry_delay = 10
    
    for attempt in range(max_retries):
        logs = test_env.functions_controller.get_container_logs()
        matches = [line for line in logs.split('\n') if "Initialized SDK types enabled flag" in line]
        
        print(f"Attempt: {attempt}")
        if len(matches) >= 2:
            break
        
        if attempt < max_retries - 1:
            print(f"⏳ Found {len(matches)} matches, retrying in {retry_delay}s (attempt {attempt + 1}/{max_retries})...")
            time.sleep(retry_delay)
    
    # Should have at least 2 matches
    assert len(matches) >= 2, \
        f"Expected at least 2 log lines but found {len(matches)}. Matches:\n" + "\n".join(matches)
    
    # Get first two matches (in case there are duplicates)
    first_two_matches = matches[:2]
    
    # Verify both expected lines appear (order may vary)
    matched_lines = "\n".join(first_two_matches)
    assert expected_first_line in matched_lines, \
        f"Expected first line not found in matches.\nExpected: {expected_first_line}\nMatches:\n{matched_lines}"
    
    assert expected_second_line in matched_lines, \
        f"Expected second line not found in matches.\nExpected: {expected_second_line}\nMatches:\n{matched_lines}"
    
    # Print success message
    print(f"✅ Test passed: SDK types value='{sdk_types_value}'")
    for match in first_two_matches:
        print(f"   📝 {match}")


def test_sdk_types_disabled_explicitly(test_env):
    """
    Test that SDK types flag is disabled when explicitly set to 'false'.
    
    Expects two log lines:
    1. Initial: "Initialized SDK types enabled flag: true" (default on WorkerInit)
    2. After reload: "Initialized SDK types enabled flag: false" (from env var 'false')
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value='false',
        expected_first_line=EXPECTED_WORKERINIT_TRUE_NULL,
        expected_second_line=EXPECTED_RELOAD_FALSE
    )


def test_sdk_types_enabled_explicitly(test_env):
    """
    Test that SDK types flag is enabled when explicitly set to 'true'.
    
    Expects two log lines:
    1. Initial: "Initialized SDK types enabled flag: true" (default on WorkerInit)
    2. After reload: "Initialized SDK types enabled flag: true" (from env var 'true')
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value='true',
        expected_first_line=EXPECTED_WORKERINIT_TRUE_NULL,
        expected_second_line=EXPECTED_RELOAD_TRUE
    )


def test_sdk_types_default_when_not_specified(test_env):
    """
    Test that SDK types flag defaults to true when not specified.
    
    Expects two log lines:
    1. Initial: "Initialized SDK types enabled flag: true" (default on WorkerInit)
    2. After reload: "Initialized SDK types enabled flag: true" (default when env var not set)
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value=None,  # Don't include JAVA_ENABLE_SDK_TYPES in env vars
        expected_first_line=EXPECTED_WORKERINIT_TRUE_NULL,
        expected_second_line=EXPECTED_RELOAD_TRUE_NULL
    )


if __name__ == "__main__":
    """Allow running tests directly with python"""
    pytest.main([__file__, "-v", "-s"])
