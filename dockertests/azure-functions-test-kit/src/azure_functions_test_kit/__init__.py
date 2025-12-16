# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Azure Functions Test Kit - Testing utilities for Azure Functions.

Usage:
    from azure_functions_test_kit import LinuxConsumptionTestEnvironment
    
    @pytest.fixture
    def test_env():
        with LinuxConsumptionTestEnvironment(apps_to_upload=['MyApp']) as env:
            yield env
"""

__version__ = "0.1.0"

# Export main classes for easy import
from .controllers.azurite_container_controller import AzuriteContainerController
from .controllers.functions_container_controller import FunctionsContainerController
from .environments.consumption import LinuxConsumptionTestEnvironment

__all__ = [
    "AzuriteContainerController",
    "FunctionsContainerController",
    "LinuxConsumptionTestEnvironment"
]
