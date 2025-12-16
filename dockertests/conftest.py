"""
Shared pytest fixtures for Azure Functions Java worker tests.
"""

import pytest
import sys
from pathlib import Path
from dotenv import load_dotenv


@pytest.fixture(scope="session", autouse=True)
def load_env():
    """Load .env file before running tests"""
    env_file = Path(__file__).parent / '.env'
    if env_file.exists():
        load_dotenv(env_file)
        print(f"✅ Loaded configuration from {env_file}")
    else:
        print(f"⚠️  No .env file found at {env_file}, using defaults")
