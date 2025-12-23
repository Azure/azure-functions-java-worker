#!/usr/bin/env python3
"""
Pytest tests for timezone handling in Azure Functions Java worker.
Tests verify that the TZ environment variable is correctly applied
and the Java runtime returns the expected timezone.
"""

import pytest
import requests
from azure_functions_test_kit import LinuxConsumptionTestEnvironment


# Configuration
APP_NAME = "TimezoneCheck"
DEFAULT_TIMEZONE = "Etc/UTC"


@pytest.fixture
def test_env():
    """Create a fresh test environment for each test"""
    with LinuxConsumptionTestEnvironment(apps_to_upload=[APP_NAME]) as env:
        yield env


def verify_timezone(test_env, tz_value, expected_timezone):
    """
    Helper function to verify timezone is correctly set based on TZ env variable.
    
    Args:
        test_env: TestEnvironment fixture
        tz_value: Value for TZ environment variable (None to omit)
        expected_timezone: Expected timezone ID returned by the function
    """
    # Build environment variables
    app_url = test_env.get_blob_sas_url(APP_NAME)
    env_vars = {
        'SCM_RUN_FROM_PACKAGE': app_url,
        'AzureWebJobsStorage': test_env.docker_storage_connection_string
    }
    
    # Add TZ if specified
    if tz_value is not None:
        env_vars['TZ'] = tz_value
    
    # Assign container with environment variables
    test_env.functions_controller.assign_container(env=env_vars)
    
    # Wait for functions to be loaded
    assert test_env.functions_controller.wait_for_host_running(timeout=360), \
        "Functions host did not reach running state within timeout"

    assert test_env.functions_controller.wait_for_functions_loaded(timeout=360), \
        "Functions did not load within timeout"
    
    # Invoke the function and get the timezone
    req = requests.Request('GET', f'{test_env.functions_controller.url}/api/GetTimezone')
    response = test_env.functions_controller.send_request(req, post_assignment=True)
    
    # Verify response is successful
    assert response.status_code == 200, \
        f"Function returned status {response.status_code}. Response: {response.text}"
    
    actual_timezone = response.text.strip()
    
    # Verify the timezone matches expected
    assert actual_timezone == expected_timezone, \
        f"Timezone mismatch. Expected: {expected_timezone}, Actual: {actual_timezone}"
    
    tz_display = tz_value if tz_value is not None else "not set (default)"
    print(f"✅ Test passed: TZ='{tz_display}' returned timezone '{actual_timezone}'")


def test_timezone_default_when_not_set(test_env):
    """
    Test that timezone defaults to Etc/UTC when TZ env variable is not set.
    """
    verify_timezone(
        test_env=test_env,
        tz_value=None,  # Don't include TZ in env vars
        expected_timezone=DEFAULT_TIMEZONE
    )


def test_timezone_america_new_york(test_env):
    """
    Test that TZ=America/New_York returns the correct timezone.
    """
    verify_timezone(
        test_env=test_env,
        tz_value='America/New_York',
        expected_timezone='America/New_York'
    )


if __name__ == "__main__":
    """Allow running tests directly with python"""
    pytest.main([__file__, "-v", "-s"])
