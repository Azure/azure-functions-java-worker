import pytest
from unittest.mock import MagicMock, patch
from azure_functions_test_kit import LinuxConsumptionTestEnvironment

# Patch the class in the plugin module where it is instantiated
@pytest.fixture(autouse=True)
def mock_env_class():
    with patch("azure_functions_test_kit.plugin.LinuxConsumptionTestEnvironment") as mock:
        # Setup the mock instance
        mock_instance = mock.return_value
        mock_instance.runtime = "python"
        yield mock

def test_environment_fixture(test_env, mock_env_class):
    """Test that the test_env fixture is correctly parametrized and initialized."""
    if test_env is None:
        pytest.skip("Environment not configured")
        
    # Verify start was called on the instance returned by the constructor
    test_env.start.assert_called_once()
    
@pytest.mark.storage("real")
def test_real_storage_marker(test_env, mock_env_class):
    """Test that the storage marker works."""
    # Verify constructor was called with use_real_storage=True
    call_args = mock_env_class.call_args
    assert call_args is not None
    _, kwargs = call_args
    assert kwargs.get("use_real_storage") is True

def test_default_storage(test_env, mock_env_class):
    """Test that default storage is Azurite."""
    # Verify constructor was called with use_real_storage=False
    call_args = mock_env_class.call_args
    assert call_args is not None
    _, kwargs = call_args
    assert kwargs.get("use_real_storage") is False
