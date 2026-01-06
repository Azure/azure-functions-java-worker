"""Test that the pytest plugin loads .env files correctly."""
import os
from pathlib import Path
from unittest.mock import patch, MagicMock


def test_plugin_loads_dotenv():
    """Test that the plugin's pytest_configure function loads .env files."""
    from azure_functions_test_kit.plugin import pytest_configure
    
    # Create a mock config object (pytest doesn't require anything from it in our plugin)
    mock_config = MagicMock()
    
    with patch('azure_functions_test_kit.plugin.load_dotenv') as mock_load_dotenv:
        with patch('azure_functions_test_kit.plugin.Path.cwd') as mock_cwd:
            # Mock that .env file exists in current directory
            mock_path = MagicMock()
            mock_path.exists.return_value = True
            mock_cwd.return_value = MagicMock(__truediv__=lambda self, x: mock_path)
            
            pytest_configure(mock_config)
            
            # Verify load_dotenv was called
            mock_load_dotenv.assert_called_once()

