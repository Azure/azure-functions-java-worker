#!/usr/bin/env python3
"""
Example usage script showing different ways to test Azure Functions containers.
"""

import time
import requests
from pathlib import Path
from dotenv import load_dotenv
from utils import LinuxConsumptionTestEnvironment, FunctionsContainerController

# Load environment variables from .env file
env_file = Path(__file__).parent / '.env'
if env_file.exists():
    load_dotenv(env_file)
    print(f"✅ Loaded configuration from {env_file}")
else:
    print(f"⚠️  No .env file found at {env_file}, using defaults")


def example_with_test_environment():
    """Example: Use TestEnvironment for complete test setup with Azurite and app management"""
    print("=" * 80)
    print("Example 1: Using TestEnvironment (Recommended)")
    print("=" * 80)
    
    # TestEnvironment handles Azurite, app uploads, and SAS token generation
    # Configuration can be set via environment variables or parameters
    # Set FUNCTIONS_TEST_WORKER_DIR env var or it will auto-detect ./worker directory
    with LinuxConsumptionTestEnvironment(
        apps_to_upload=["app"]  # Most config comes from env vars or defaults
    ) as env:
        
        # List available apps
        print("\n📋 Available apps:")
        for app_name in env.list_uploaded_apps().keys():
            print(f"   - {app_name}")
        
        # Get app URL and assign container directly through the controller
        app_url = env.get_blob_sas_url('app')  # Supports with or without extension
        env.functions_controller.assign_container(
            env={
                'SCM_RUN_FROM_PACKAGE': app_url,
                'JAVA_ENABLE_SDK_TYPES': 'false',
                'JAVA_ENABLE_OPENTELEMETRY': 'true',
                'AzureWebJobsStorage': env.docker_storage_connection_string,
                'PDFProcessorSTORAGE': env.docker_storage_connection_string
            }
        )
        
        # Wait for functions to be loaded
        if env.functions_controller.wait_for_functions_loaded(timeout=120):
            # Test an endpoint if you have one
            test_function_endpoint(env.functions_controller, "/api/GetEnvVariables")
        else:
            print("⚠️ Functions not loaded within timeout, but continuing...")
        
    print("\n✅ Test completed - containers automatically cleaned up")


def test_function_endpoint(controller: FunctionsContainerController, endpoint: str):
    """Test a function endpoint after container assignment"""
    print(f"\n🧪 Testing endpoint {endpoint}...")
    
    try:
        req = requests.Request('GET', f'{controller.url}{endpoint}')
        response = controller.send_request(req, post_assignment=True)
        
        print(f"📊 Response Status: {response.status_code}")
        
        if response.ok:
            print("✅ Endpoint responded successfully!")
            try:
                json_data = response.json()
                print(f"📄 Response JSON: {json_data}")
            except:
                print(f"📄 Response Text: {response.text}")
        else:
            print(f"❌ Endpoint failed with status {response.status_code}")
            print(f"📄 Response Text: {response.text}")
            
            # Also print container logs for debugging
            print("\n🔍 Container logs (all lines):")
            logs = controller.get_container_logs()
            log_lines = logs.split('\n')
            for line in log_lines:
                if line.strip():
                    print(f"   {line}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ Request failed: {e}")


if __name__ == "__main__":
    print("🚀 Azure Functions Container Testing Example")
    
    
    try:
        example_with_test_environment()
        
    except Exception as e:
        print(f"❌ Error during execution: {e}")
        import traceback
        traceback.print_exc()
