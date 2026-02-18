# Azure Functions Test Kit

Reusable test kit for testing Azure Functions workers in Docker containers.

## Features

### 🔄 Automatic Test Retries

Flaky tests are automatically retried using [`pytest-rerunfailures`](https://github.com/pytest-dev/pytest-rerunfailures). This is especially useful for integration tests that may fail due to timing issues, network hiccups, or container startup delays.

**Default behavior** (configured in `pyproject.toml` via `addopts`):
- Tests are automatically retried up to **2 times** on failure
- **5 seconds delay** between retry attempts

**Override via command line:**

```bash
# Disable retries completely
pytest --reruns 0

# Set custom retry count
pytest --reruns 5 --reruns-delay 3
```

**Override via environment variable (CI pipelines):**

```bash
# pytest's built-in env var for injecting CLI options
export PYTEST_ADDOPTS="--reruns 3 --reruns-delay 10"
```

### 🐳 Automatic Container Logs on Failure

When a test fails, the test kit automatically captures and displays the container logs. This helps with debugging by showing exactly what happened inside the container.

**Example output:**
```
================================================================================
🐳 CONTAINER LOGS FOR FAILED TEST
================================================================================
[2026-01-13 10:30:15] Starting Azure Functions host...
[2026-01-13 10:30:16] Worker initialization started...
[2026-01-13 10:30:17] Error: Failed to load function app
================================================================================
```

### 📋 Environment Configuration

The plugin automatically loads configuration from `.env` files in your test directory or parent directories (up to 3 levels).

**Supported environment variables:**

```env
# Storage Configuration
FUNCTIONS_TEST_USE_AZURITE=true                    # Use Azurite emulator (default: true)
FUNCTIONS_TEST_STORAGE_CONNECTION_STRING=...       # Azure Storage connection string (for real storage)

# Runtime Configuration
FUNCTIONS_TEST_RUNTIME=java                        # Runtime: java, python, etc. (default: java)
FUNCTIONS_TEST_RUNTIME_VERSION=21                  # Runtime version (default: 21)
FUNCTIONS_TEST_HOST_VERSION=4                      # Functions host version (default: 4)

# Test Configuration
FUNCTIONS_TEST_APPS_DIR=./app-packages            # Directory with app packages (default: ./app-packages)
FUNCTIONS_TEST_WORKER_DIR=/path/to/worker         # Custom worker directory to mount

# Retry Configuration: use PYTEST_ADDOPTS or pyproject.toml addopts
# See "Automatic Test Retries" section above
```

## Installation

```bash
pip install -e ./azure-functions-test-kit
```

The plugin is automatically activated via pytest's entry point mechanism.

## Usage

### Basic Test Example

```python
import pytest
from azure_functions_test_kit import LinuxConsumptionTestEnvironment

@pytest.fixture
def test_env():
    """Create a fresh test environment for each test"""
    with LinuxConsumptionTestEnvironment(apps_to_upload=['MyApp']) as env:
        yield env

def test_my_function(test_env):
    # Test automatically gets retries and log capture
    response = requests.get(f"{test_env.url}/api/MyFunction")
    assert response.status_code == 200
```

### Disabling Retries for Specific Tests

```python
@pytest.mark.no_rerun  # This test will not be retried
def test_critical_function(test_env):
    # This test will fail immediately without retries
    pass
```

## How It Works

1. **Plugin Discovery**: pytest automatically discovers the plugin via the `pytest11` entry point
2. **Environment Loading**: `.env` files are loaded during `pytest_configure`
3. **Retry Configuration**: `pytest-rerunfailures` handles retries via `addopts` in `pyproject.toml`
4. **Test Execution**: Tests run with automatic retry on failure
5. **Log Capture**: On failure, container logs are captured and displayed

## Benefits

- **Reduced flakiness**: Automatic retries handle transient failures
- **Better debugging**: Container logs are immediately available on failure
- **Flexible configuration**: Override defaults via environment variables or command line
- **Zero code changes**: Works automatically with existing tests
