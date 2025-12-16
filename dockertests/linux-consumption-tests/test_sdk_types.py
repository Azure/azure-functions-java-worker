#!/usr/bin/env python3
"""
Pytest tests for SDK types flag behavior in Azure Functions Java worker.
Tests verify that the JAVA_ENABLE_SDK_TYPES environment variable is correctly
processed during both WorkerInit and FunctionEnvironmentReload.
"""

import pytest
import time
from azure_functions_test_kit import LinuxConsumptionTestEnvironment


# Configuration
APP_NAME = "BlobSdkType"
NONSENSE_VALUE = "nonsense"

# Expected log line patterns
EXPECTED_RELOAD_FALSE = "Initialized SDK types enabled flag: false (from 'JAVA_ENABLE_SDK_TYPES' environment variable : 'false')"
EXPECTED_RELOAD_TRUE = "Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : 'true')"
EXPECTED_RELOAD_TRUE_NULL = "Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : null)"
EXPECTED_RELOAD_TRUE_NONSENSE = f"Initialized SDK types enabled flag: true (from 'JAVA_ENABLE_SDK_TYPES' environment variable : '{NONSENSE_VALUE}')"


@pytest.fixture
def test_env():
    """Create a fresh test environment for each test"""
    with LinuxConsumptionTestEnvironment(apps_to_upload=[APP_NAME]) as env:
        yield env


def verify_sdk_types_logs(test_env, sdk_types_value, expected_reload_line):
    """
    Helper function to verify SDK types flag behavior after FunctionEnvironmentReload.
    
    Args:
        test_env: TestEnvironment fixture
        sdk_types_value: Value for JAVA_ENABLE_SDK_TYPES env var (None to omit)
        expected_reload_line: Expected log line from FunctionEnvironmentReload
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
    max_retries = 6
    retry_delay = 10
    
    for attempt in range(max_retries):
        logs = test_env.functions_controller.get_container_logs()
        matches = [line for line in logs.split('\n') if "Initialized SDK types enabled flag" in line]
        
        print(f"Attempt: {attempt}, found {len(matches)} matches")
        
        # Check if the expected reload line is present
        if any(expected_reload_line in match for match in matches):
            break
        
        if attempt < max_retries - 1:
            print(f"⏳ Expected line not found yet, retrying in {retry_delay}s (attempt {attempt + 1}/{max_retries})...")
            time.sleep(retry_delay)
    
    # Verify the expected reload line appears
    matched_lines = "\n".join(matches)
    assert expected_reload_line in matched_lines, \
        f"Expected reload line not found in matches.\nExpected: {expected_reload_line}\nAll matches:\n{matched_lines}"
    
    # Print success message
    print(f"✅ Test passed: SDK types value='{sdk_types_value}'")
    print(f"   📝 Found expected line: {expected_reload_line}")
    print(f"   Total matches: {len(matches)}")


def test_sdk_types_disabled_explicitly(test_env):
    """
    Test that SDK types flag is disabled when explicitly set to 'false'.
    
    Expects reload log line: "Initialized SDK types enabled flag: false" (from env var 'false')
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value='false',
        expected_reload_line=EXPECTED_RELOAD_FALSE
    )


def test_sdk_types_enabled_explicitly(test_env):
    """
    Test that SDK types flag is enabled when explicitly set to 'true'.
    
    Expects reload log line: "Initialized SDK types enabled flag: true" (from env var 'true')
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value='true',
        expected_reload_line=EXPECTED_RELOAD_TRUE
    )


def test_sdk_types_default_when_not_specified(test_env):
    """
    Test that SDK types flag defaults to true when not specified.
    
    Expects reload log line: "Initialized SDK types enabled flag: true" (default when env var not set)
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value=None,  # Don't include JAVA_ENABLE_SDK_TYPES in env vars
        expected_reload_line=EXPECTED_RELOAD_TRUE_NULL
    )


def test_sdk_types_enabled_with_nonsense_value(test_env):
    """
    Test that SDK types flag defaults to true when set to a nonsense value.
    
    Any value other than 'false' should enable SDK types.
    Expects reload log line: "Initialized SDK types enabled flag: true" (from env var 'nonsense')
    """
    verify_sdk_types_logs(
        test_env=test_env,
        sdk_types_value=NONSENSE_VALUE,
        expected_reload_line=EXPECTED_RELOAD_TRUE_NONSENSE
    )


if __name__ == "__main__":
    """Allow running tests directly with python"""
    pytest.main([__file__, "-v", "-s"])
