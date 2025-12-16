import sys
import os

# Add src to path for testing without installation
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../src')))

from azure_functions_test_kit import (
    AzuriteContainerController,
    FunctionsContainerController,
    LinuxConsumptionTestEnvironment
)

def test_imports():
    assert AzuriteContainerController is not None
    assert FunctionsContainerController is not None
    assert LinuxConsumptionTestEnvironment is not None
    print("Imports successful!")

if __name__ == "__main__":
    test_imports()
