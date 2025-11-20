#!/usr/bin/env python3
"""
Example usage script showing different ways to test Azure Functions containers.
"""

import time
import requests
from utils import TestEnvironment, FunctionsContainerController


def example_with_test_environment():
    """Example: Use TestEnvironment for complete test setup with Azurite and app management"""
    print("=" * 80)
    print("Example 1: Using TestEnvironment (Recommended)")
    print("=" * 80)
    
    # TestEnvironment handles Azurite, app uploads, and SAS token generation
    with TestEnvironment(
        use_azurite=True,
        apps_directory="./apps",
        apps_to_upload=["app"],  # List of app names (without extension) to upload
        runtime="java",
        runtime_version="21",
        host_version="4",
        docker_flags=[
            "-v", r"D:\OneDrive\OneDrive - Microsoft\Documents\jw\repos\azure-functions-java-worker\java:/azure-functions-host/workers/java"
        ]
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
                'AzureWebJobsStorage': env.docker_storage_connection_string,
                'PDFProcessorSTORAGE': env.docker_storage_connection_string
            }
        )
        
        # Wait for functions to be loaded
        if env.functions_controller.wait_for_functions_loaded(timeout=120):
            # Functions are loaded, proceed with tests
            test_sdk_types_flag_in_logs(env.functions_controller)
        else:
            print("⚠️ Functions not loaded within timeout, but continuing...")
        
        # Test an endpoint if you have one
        # test_function_endpoint(env.functions_controller, "/api/YourFunction")
        
    print("\n✅ Test completed - containers automatically cleaned up")


def example_with_controller_only():
    """Example: Use FunctionsContainerController directly (manual setup required)"""
    print("=" * 80)
    print("Example 2: Using FunctionsContainerController directly")
    print("=" * 80)
    
    # You need to manually provide connection string and app package URL
    # This assumes you have Azurite running separately on default ports
    cs_azurite = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://host.docker.internal:10000/devstoreaccount1;QueueEndpoint=http://host.docker.internal:10001/devstoreaccount1;TableEndpoint=http://host.docker.internal:10002/devstoreaccount1;"
    
    with FunctionsContainerController(
        runtime="java",
        runtime_version="21",
        host_version="4",
        docker_flags=[
            "-v", r"D:\OneDrive\OneDrive - Microsoft\Documents\jw\repos\azure-functions-java-worker\java:/azure-functions-host/workers/java"
        ]
    ) as controller:
        
        env_vars = {
            "SCM_RUN_FROM_PACKAGE": "http://host.docker.internal:10000/devstoreaccount1/app/app.squashfs?se=2025-12-31T23%3A59%3A59Z&sp=r&sv=2022-11-02&sr=b&sig=8cxj%2FQAfsJ7EOgW29Tl9rPHUc8P81kME%2FIYD9rB4fkE%3D",
            "AzureWebJobsStorage": cs_azurite,
            "PDFProcessorSTORAGE": cs_azurite,
            "JAVA_ENABLE_SDK_TYPES": "false"
        }
        
        controller.assign_container(env=env_vars)
        
        # Wait for host to be running (use this when you don't have functions)
        if controller.wait_for_host_running(timeout=120):
            test_sdk_types_flag_in_logs(controller)
        
    print("\n✅ Container automatically cleaned up")


def example_assign_existing_container():
    """Example: Assign an already-running container (advanced use case)"""
    print("=" * 80)
    print("Example 3: Assigning existing container")
    print("=" * 80)
    
    # This assumes you have a container already running on port 8080
    controller = FunctionsContainerController(
        container_url="http://localhost:8080",
        runtime="java",
        runtime_version="21",
        site_name="your-site-name"  # Must match the container's WEBSITE_SITE_NAME
    )
    
    cs_azurite = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://host.docker.internal:10000/devstoreaccount1;QueueEndpoint=http://host.docker.internal:10001/devstoreaccount1;TableEndpoint=http://host.docker.internal:10002/devstoreaccount1;"
    
    env_vars = {
        "SCM_RUN_FROM_PACKAGE": "http://host.docker.internal:10000/devstoreaccount1/app/app.squashfs?se=2025-12-31T23%3A59%3A59Z&sp=r&sv=2022-11-02&sr=b&sig=8cxj%2FQAfsJ7EOgW29Tl9rPHUc8P81kME%2FIYD9rB4fkE%3D",
        "AzureWebJobsStorage": cs_azurite,
        "PDFProcessorSTORAGE": cs_azurite,
        "JAVA_ENABLE_SDK_TYPES": "false"
    }
    
    controller.assign_container(env=env_vars)
    
    # Wait for functions to load (or use wait_for_host_running if no functions expected)
    controller.wait_for_functions_loaded(timeout=120)
    
    test_sdk_types_flag_in_logs(controller)
    
    print("\n✅ Assignment completed")
    return controller


def test_function_endpoint(controller: FunctionsContainerController, endpoint: str):
    """Test a function endpoint after container assignment"""
    print(f"\n🧪 Testing endpoint {endpoint}...")
    
    try:
        req = requests.Request('GET', f'{controller.url}{endpoint}')
        response = controller.send_request(req)
        
        print(f"📊 Response Status: {response.status_code}")
        
        if response.ok:
            print("✅ Endpoint responded successfully!")
            try:
                json_data = response.json()
                print(f"📄 Response JSON: {json_data}")
            except:
                print(f"📄 Response Text: {response.text[:200]}")
        else:
            print(f"❌ Endpoint failed with status {response.status_code}")
            print(f"📄 Response Text: {response.text[:200]}")
            
    except requests.exceptions.RequestException as e:
        print(f"❌ Request failed: {e}")


def test_sdk_types_flag_in_logs(controller: FunctionsContainerController):
    """Check container logs for SDK types initialization messages"""
    print("\n🔍 Checking container logs for SDK types flag...")
    
    try:
        logs = controller.get_container_logs()
        
        # Search for lines containing "Initialized SDK types enabled flag"
        matches = [line for line in logs.split('\n') if "Initialized SDK types enabled flag" in line]
        
        if matches:
            print(f"✅ Found {len(matches)} matching log line(s):")
            for match in matches:
                print(f"   📝 {match}")
        else:
            print("❌ No matching log lines found for 'Initialized SDK types enabled flag'")
            
    except Exception as e:
        print(f"❌ Failed to check logs: {e}")


if __name__ == "__main__":
    print("🚀 Azure Functions Container Testing Examples")
    print("\nChoose an option:")
    print("1. Use TestEnvironment (Recommended - handles everything)")
    print("2. Use FunctionsContainerController only (manual setup)")
    print("3. Assign existing container (advanced)")
    
    choice = input("\nEnter choice (1, 2, or 3): ").strip()
    
    try:
        if choice == "1":
            example_with_test_environment()
            
        elif choice == "2":
            example_with_controller_only()
            
        elif choice == "3":
            example_assign_existing_container()
            
        else:
            print("❌ Invalid choice. Please enter 1, 2, or 3.")
            exit(1)
        
    except Exception as e:
        print(f"❌ Error during execution: {e}")
        import traceback
        traceback.print_exc()
