#!/usr/bin/env python3
"""
Pytest tests for timezone handling in Azure Functions Java worker.
Tests verify that the WEBSITE_TIME_ZONE and TZ environment variables are correctly applied
and the Java runtime returns the expected timezone.
WEBSITE_TIME_ZONE takes precedence over TZ.
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


def verify_timezone(test_env, website_tz=None, tz=None, expected_timezone=DEFAULT_TIMEZONE):
    """
    Helper function to verify timezone is correctly set based on env variables.
    
    Args:
        test_env: TestEnvironment fixture
        website_tz: Value for WEBSITE_TIME_ZONE environment variable (None to omit)
        tz: Value for TZ environment variable (None to omit)
        expected_timezone: Expected timezone ID returned by the function
    """
    # Build environment variables
    app_url = test_env.get_blob_sas_url(APP_NAME)
    env_vars = {
        'SCM_RUN_FROM_PACKAGE': app_url,
        'AzureWebJobsStorage': test_env.docker_storage_connection_string
    }
    
    # Add timezone variables if specified
    if website_tz is not None:
        env_vars['WEBSITE_TIME_ZONE'] = website_tz
    if tz is not None:
        env_vars['TZ'] = tz
    
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
    
    # Build display message
    tz_sources = []
    if website_tz is not None:
        tz_sources.append(f"WEBSITE_TIME_ZONE='{website_tz}'")
    if tz is not None:
        tz_sources.append(f"TZ='{tz}'")
    if not tz_sources:
        tz_sources.append("not set (default)")
    
    print(f"✅ Test passed: {', '.join(tz_sources)} returned timezone '{actual_timezone}'")


def test_timezone_default_when_not_set(test_env):
    """
    Test that timezone defaults to Etc/UTC when neither WEBSITE_TIME_ZONE nor TZ is set.
    """
    verify_timezone(
        test_env=test_env,
        website_tz=None,
        tz=None,
        expected_timezone=DEFAULT_TIMEZONE
    )


def test_timezone_website_time_zone_takes_precedence(test_env):
    """
    Test that WEBSITE_TIME_ZONE takes precedence over TZ when both are set.
    """
    verify_timezone(
        test_env=test_env,
        website_tz='America/New_York',
        tz='Europe/London',  # This should be ignored
        expected_timezone='America/New_York'
    )


def test_timezone_website_time_zone_america_new_york(test_env):
    """
    Test that WEBSITE_TIME_ZONE=America/New_York returns the correct timezone.
    """
    verify_timezone(
        test_env=test_env,
        website_tz='America/New_York',
        expected_timezone='America/New_York'
    )


def test_timezone_tz_fallback_when_website_time_zone_not_set(test_env):
    """
    Test that TZ is used as fallback when WEBSITE_TIME_ZONE is not set.
    """
    verify_timezone(
        test_env=test_env,
        tz='Europe/London',
        expected_timezone='Europe/London'
    )

if __name__ == "__main__":
    """Allow running tests directly with python"""
    pytest.main([__file__, "-v", "-s"])
