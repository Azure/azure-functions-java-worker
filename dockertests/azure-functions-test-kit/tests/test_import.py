"""Test that the package imports work correctly."""
from azure_functions_test_kit import (
    AzuriteContainerController,
    FunctionsContainerController,
    LinuxConsumptionTestEnvironment
)


def test_imports():
    """Verify all main classes can be imported."""
    assert AzuriteContainerController is not None
    assert FunctionsContainerController is not None
    assert LinuxConsumptionTestEnvironment is not None

