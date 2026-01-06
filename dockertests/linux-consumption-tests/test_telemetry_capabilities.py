#!/usr/bin/env python3
"""
Pytest tests for telemetry capabilities in Azure Functions Java worker.
Tests verify that WorkerOpenTelemetryEnabled and WorkerApplicationInsightsLoggingEnabled
capabilities are set based on environment variables during FunctionEnvironmentReload.
"""

import pytest
from azure_functions_test_kit import LinuxConsumptionTestEnvironment


@pytest.fixture
def test_env():
    """Create a fresh test environment for each test with no apps uploaded"""
    with LinuxConsumptionTestEnvironment(apps_to_upload=[]) as env:
        yield env


def verify_telemetry_capabilities(test_env, env_vars, should_have_capabilities=True):
    """
    Helper function to verify telemetry capabilities in logs.
    
    Args:
        test_env: TestEnvironment fixture
        env_vars: Dictionary of environment variables to send
        should_have_capabilities: If True, verify capabilities are present; if False, verify they're absent
    """
    # Ensure AzureWebJobsStorage is included
    env_vars['AzureWebJobsStorage'] = test_env.docker_storage_connection_string
    
    # Assign container with environment variables
    test_env.functions_controller.assign_container(env=env_vars)
    
    # Wait for host to be running
    assert test_env.functions_controller.wait_for_host_running(timeout=360), \
        "Functions host did not reach running state within timeout"
    
    # Get container logs
    logs = test_env.functions_controller.get_container_logs()
    
    # Check for capabilities
    has_opentelemetry = "WorkerOpenTelemetryEnabled" in logs
    has_appinsights = "WorkerApplicationInsightsLoggingEnabled" in logs
    
    if should_have_capabilities:
        # Verify both capabilities are present
        assert has_opentelemetry, \
            "Expected 'WorkerOpenTelemetryEnabled' to appear in logs"
        
        assert has_appinsights, \
            "Expected 'WorkerApplicationInsightsLoggingEnabled' to appear in logs"
        
        # Print success message with relevant log lines
        print("✅ Test passed: Telemetry capabilities correctly set")
        for line in logs.split('\n'):
            if "WorkerOpenTelemetryEnabled" in line or "WorkerApplicationInsightsLoggingEnabled" in line:
                print(f"   📝 {line}")
    else:
        # Verify capabilities are NOT present
        assert not has_opentelemetry, \
            "Expected 'WorkerOpenTelemetryEnabled' NOT to appear in logs when telemetry disabled"
        
        assert not has_appinsights, \
            "Expected 'WorkerApplicationInsightsLoggingEnabled' NOT to appear in logs when telemetry disabled"
        
        print("✅ Test passed: Telemetry capabilities correctly NOT set when disabled")


def test_opentelemetry_enabled_sets_capabilities(test_env):
    """
    Test that WorkerOpenTelemetryEnabled and WorkerApplicationInsightsLoggingEnabled
    capabilities are set when JAVA_ENABLE_OPENTELEMETRY is 'true'.
    """
    verify_telemetry_capabilities(
        test_env=test_env,
        env_vars={'JAVA_ENABLE_OPENTELEMETRY': 'true'},
        should_have_capabilities=True
    )


def test_appinsights_enabled_sets_capabilities(test_env):
    """
    Test that WorkerOpenTelemetryEnabled and WorkerApplicationInsightsLoggingEnabled
    capabilities are set when JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY is 'true'.
    """
    verify_telemetry_capabilities(
        test_env=test_env,
        env_vars={'JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY': 'true'},
        should_have_capabilities=True
    )


def test_both_telemetry_flags_enabled_sets_capabilities(test_env):
    """
    Test that WorkerOpenTelemetryEnabled and WorkerApplicationInsightsLoggingEnabled
    capabilities are set when both telemetry flags are 'true'.
    """
    verify_telemetry_capabilities(
        test_env=test_env,
        env_vars={
            'JAVA_ENABLE_OPENTELEMETRY': 'true',
            'JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY': 'true'
        },
        should_have_capabilities=True
    )


def test_telemetry_disabled_no_capabilities(test_env):
    """
    Test that WorkerOpenTelemetryEnabled and WorkerApplicationInsightsLoggingEnabled
    capabilities are NOT set when telemetry flags are 'false' or missing.
    """
    verify_telemetry_capabilities(
        test_env=test_env,
        env_vars={
            'JAVA_ENABLE_OPENTELEMETRY': 'false',
            'JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY': 'false'
        },
        should_have_capabilities=False
    )


if __name__ == "__main__":
    """Allow running tests directly with python"""
    pytest.main([__file__, "-v", "-s"])
