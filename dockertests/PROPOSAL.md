# Strategy for Reusable Azure Functions Test Suite

To make your testing suite available for other projects as a tool, the best practice in the Python ecosystem is to package it as a **Pytest Plugin** and a **Library**.

## Core Concept

1.  **The Library**: Contains the heavy lifting logic (e.g., `LinuxConsumptionTestEnvironment`, container controllers).
2.  **The Plugin**: Integrates with `pytest` to provide fixtures (setup/teardown of environments) and command-line options.
3.  **The Standard Tests**: A set of base test classes that other projects can inherit from to run standard compliance/integration tests.

## Proposed Structure

You would restructure your `dockertests` folder (or create a new repo) like this:

```text
azure-functions-test-kit/
├── pyproject.toml                  # Defines dependencies and plugin entry point
├── src/
│   └── azure_functions_test_kit/
│       ├── __init__.py
│       ├── plugin.py               # Pytest hooks and fixture definitions
│       ├── environments/           # Environment implementations
│       │   ├── __init__.py
│       │   ├── base.py             # Abstract Base Class (defines interface)
│       │   ├── consumption.py      # Linux Consumption logic
│       │   ├── dedicated.py        # Linux Dedicated logic
│       │   └── flex.py             # Flex Consumption logic
│       ├── controllers/            # Container controllers (Azurite, Functions)
│       └── standard_tests/         # Reusable test suites
│           ├── __init__.py
│           ├── base.py             # Base class for tests
│           └── sdk_tests.py        # Example: SDK type tests
└── tests/                          # Tests for the kit itself
```

## 1. The Pytest Plugin (`plugin.py`)

This file exposes your environment as a fixture. It uses **Dynamic Parameterization** to allow running tests against multiple environments in a single session.

```python
import pytest
from .environments.consumption import LinuxConsumptionEnvironment
from .environments.dedicated import LinuxDedicatedEnvironment
from .environments.flex import FlexConsumptionEnvironment

def pytest_addoption(parser):
    group = parser.getgroup("azure-functions-test-kit")
    group.addoption("--app-dir", action="store", default="./apps", help="Directory containing function apps")
    group.addoption("--runtime", action="store", default="java", help="Functions runtime to use")
    group.addoption(
        "--env-types", 
        action="store", 
        default="consumption", 
        help="Comma-separated list of environments to test (e.g. 'consumption,dedicated')"
    )
    # Storage options
    group.addoption("--use-real-storage", action="store_true", help="Disable Azurite and use real storage")
    group.addoption("--storage-connection", action="store", help="Override storage connection string")

def pytest_generate_tests(metafunc):
    """
    This hook runs at test collection time.
    It parametrizes 'test_env' based on:
    1. The --env-types CLI flag (active environments).
    2. The @pytest.mark.env marker (allowed environments).
    3. The @pytest.mark.storage marker (storage configuration).
    """
    if "test_env" in metafunc.fixturenames:
        # 1. Active Envs
        active_envs = set(metafunc.config.getoption("--env-types").split(","))
        
        # 2. Storage Configuration
        # Default from CLI
        default_real = metafunc.config.getoption("--use-real-storage")
        # Override from Marker
        storage_marker = metafunc.definition.get_closest_marker("storage")
        if storage_marker:
            use_real_storage = (storage_marker.args[0] == "real")
        else:
            use_real_storage = default_real

        # 3. Environment Filtering
        env_marker = metafunc.definition.get_closest_marker("env")
        if env_marker:
            allowed_envs = set(env_marker.args)
            envs_to_run = list(active_envs.intersection(allowed_envs))
        else:
            envs_to_run = list(active_envs)
            
        # 4. Parametrize with Tuple: (env_type, use_real_storage)
        # Pytest will create unique session-scoped fixtures for each unique tuple.
        params = [(e, use_real_storage) for e in envs_to_run]
        
        if params:
            metafunc.parametrize("test_env", params, indirect=True)

@pytest.fixture(scope="session")
def test_env(request):
    """
    Provides a managed test environment. 
    """
    # Unpack the tuple from parametrization
    env_type, use_real_storage = request.param
    
    app_dir = request.config.getoption("--app-dir")
    runtime = request.config.getoption("--runtime")
    storage_conn = request.config.getoption("--storage-connection")
    
    use_azurite = not use_real_storage

    # Factory logic
    if env_type == "consumption":
        env_cls = LinuxConsumptionEnvironment
    elif env_type == "dedicated":
        env_cls = LinuxDedicatedEnvironment
    elif env_type == "flex":
        env_cls = FlexConsumptionEnvironment
    else:
        raise ValueError(f"Unknown environment type: {env_type}")

    # Initialize
    env = env_cls(
        apps_directory=app_dir,
        runtime=runtime,
        use_azurite=use_azurite,
        storage_connection_string=storage_conn
    )
    
    with env:
        yield env
```

## 4. Configuration & Customization

Users can configure the environment constructor parameters in three ways (in order of precedence):

1.  **CLI Flags**: Passed to `pytest` (e.g., `--use-real-storage`).
2.  **Environment Variables**: Standard vars (e.g., `FUNCTIONS_TEST_STORAGE_CONNECTION_STRING`).
3.  **Defaults**: Defined in the environment classes.

### Adding Custom Options
In `plugin.py`, we expose these options:

```python
def pytest_addoption(parser):
    group = parser.getgroup("azure-functions-test-kit")
    # ... existing options ...
    group.addoption("--use-real-storage", action="store_true", help="Disable Azurite and use real storage")
    group.addoption("--storage-connection", action="store", help="Override storage connection string")
```

## 2. Reusable Standard Tests (`standard_tests/`)

Instead of standalone functions, wrap your standard tests in classes. This allows users to "import" tests.

```python
# src/azure_functions_test_kit/standard_tests/sdk_tests.py
import pytest

class StandardSdkTests:
    """
    Inherit from this class to run standard SDK verification tests.
    """
    
    def test_sdk_types_disabled_explicitly(self, test_env):
        # Your existing test logic here
        pass

    def test_sdk_types_enabled_by_default(self, test_env):
        # ...
        pass
```

## 3. How Other Projects Use It

A consumer (e.g., a customer's repo or another language worker) would install your package:

```bash
pip install azure-functions-test-kit
```

### Scenario A: Running Standard Tests
They create a test file `tests/test_compliance.py`:

```python
from azure_functions_test_kit.standard_tests import StandardSdkTests

class TestMyProjectCompliance(StandardSdkTests):
    # The tests from StandardSdkTests are now part of this suite.
    # They will run against the 'test_env' fixture provided by the plugin.
    pass
```

### Scenario B: Writing Custom Tests
They can write their own tests using your fixtures:

```python
def test_my_custom_logic(test_env):
    test_env.functions_controller.assign_container(...)
    # ...
```

### Scenario C: Configuration
They run pytest with your flags:

```bash
pytest --app-dir=./my-build-output --runtime=python
```

## 5. Advanced: Mixing Configurations

You can mix configurations using markers. Pytest will automatically spin up the required environment variations.

```python
# Runs on Consumption with Azurite (default)
def test_standard(test_env):
    pass

# Runs on Consumption with Real Storage
@pytest.mark.storage("real")
def test_integration(test_env):
    pass
```

## 6. Distribution and Installation

Since this is a standard Python package, you have multiple distribution options.

### Option A: PyPI (Public)
If you publish to PyPI, users simply run:
```bash
pip install azure-functions-test-kit
```

### Option B: Private Feed (Azure Artifacts)
For internal use, publish to an Azure Artifacts feed. Users configure their `pip.conf` and run the same command.

### Option C: Git Dependency (Simplest for now)
Users can install directly from your GitHub repo without publishing a package.

**In their `pyproject.toml`:**
```toml
[project]
dependencies = [
    "azure-functions-test-kit @ git+https://github.com/Azure/azure-functions-java-worker.git#subdirectory=dockertests/azure-functions-test-kit"
]
```

## 7. Example Consumer Project Structure

Here is how a project (e.g., `azure-functions-python-worker`) would look after adopting this kit.

```text
azure-functions-python-worker/
├── pyproject.toml                  # Declares dependency on 'azure-functions-test-kit'
├── src/                            # Worker source code
├── tests/
│   ├── __init__.py
│   ├── conftest.py                 # Optional: Project-specific fixtures
│   ├── integration/
│   │   ├── test_compliance.py      # Inherits from StandardSdkTests
│   │   └── test_custom_logic.py    # Uses 'test_env' fixture directly
│   └── apps/                       # Test apps (uploaded to container)
│       ├── app1/
│       └── app2/
```

### `tests/integration/test_compliance.py`
```python
from azure_functions_test_kit.standard_tests import StandardSdkTests

class TestPythonWorkerCompliance(StandardSdkTests):
    """
    Running this class will execute all standard tests defined in the kit,
    but against the Python worker (configured via CLI flags).
    """
    pass
```

### `tests/integration/test_custom_logic.py`
```python
import pytest

@pytest.mark.env("dedicated")
def test_python_specialization(test_env):
    """
    A custom test that only runs on Dedicated plans.
    """
    test_env.functions_controller.assign_container(...)
    # ...
```

## Implementation Steps

1.  **Extract Logic**: Move `utils/*.py` into a `src/azure_functions_test_kit` package.
2.  **Create Plugin**: Create `plugin.py` and register it in `pyproject.toml` under `[project.entry-points.pytest11]`.
3.  **Refactor Tests**: Convert your existing tests in `linux-consumption-tests` into class-based mixins in `src/azure_functions_test_kit/standard_tests`.
4.  **Publish**: Build and publish the package (or install as a git dependency).

This approach gives you the flexibility you asked for:
- **Tooling**: It's a library/plugin.
- **Extensibility**: Users define their own tests using your fixtures.
- **Selectivity**: Users choose which standard tests to run by inheriting (or not) from the base classes.
