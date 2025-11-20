#!/usr/bin/env python3
"""
Example usage script for the generic container assignment utility.
Shows how to assign containers for different runtimes.
"""

import os
import time
import requests
from utils.functions_container_controller import FunctionsContainerController

def assign_java_container_existing():
    """Example: Assign an already-running Java container"""
    controller = FunctionsContainerController(
        container_url="http://localhost:8080",
        runtime="java",
        runtime_version="21",
        site_name="13934045-7273-42fc-bb4f-f738236547b6"  # Match the container's WEBSITE_SITE_NAME
    )
    cs_azurite = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:10000/devstoreaccount1;QueueEndpoint=http://172.17.0.1:10001/devstoreaccount1;TableEndpoint=http://172.17.0.1:10002/devstoreaccount1;"
    env_vars = {
        "SCM_RUN_FROM_PACKAGE": "http://host.docker.internal:10000/devstoreaccount1/app/app.squashfs?se=2025-12-31T23%3A59%3A59Z&sp=r&sv=2022-11-02&sr=b&sig=8cxj%2FQAfsJ7EOgW29Tl9rPHUc8P81kME%2FIYD9rB4fkE%3D",
        "AzureWebJobsStorage": cs_azurite,
        "PDFProcessorSTORAGE": cs_azurite,
        "JAVA_ENABLE_SDK_TYPES": "false",
        "AzureWebEncryptionKey": "0F75CA46E7EBDD39E4CA6B074D1F9A5972B849A55F91A248"
    }
    
    controller.assign_container(env=env_vars, host_version="4")
    return controller


def assign_java_container_with_spawn():
    """Example: Spawn a new Java container and assign it (with context manager)"""
    cs_azurite = "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://172.17.0.1:10000/devstoreaccount1;QueueEndpoint=http://172.17.0.1:10001/devstoreaccount1;TableEndpoint=http://172.17.0.1:10002/devstoreaccount1;"
    
    # Use context manager for automatic cleanup
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
            "JAVA_ENABLE_SDK_TYPES": "false",
            "AzureWebEncryptionKey": "0F75CA46E7EBDD39E4CA6B074D1F9A5972B849A55F91A248"
        }
        
        controller.assign_container(env=env_vars, host_version="4")
        
        # Test the endpoint
        test_get_env_variables_endpoint(controller)
        
        # Check logs for SDK types flag
        test_sdk_types_flag_in_logs(controller)
        
        # Container will be automatically cleaned up when exiting this block
        return controller


def test_get_env_variables_endpoint(controller: FunctionsContainerController):
    """Test the GetEnvVariables endpoint after container assignment"""
    print("\n🧪 Testing GetEnvVariables endpoint...")
    
    # Wait a bit for the container to fully initialize after assignment
    print("⏳ Waiting 30 seconds for container to initialize...")
    time.sleep(30)

    try:
        # Use the controller's send_request method with proper authentication
        # This matches how LinuxConsumptionWebHostController sends requests
        req = requests.Request('GET', f'{controller.url}/api/GetEnvVariables')
        response = controller.send_request(req)
        
        print(f"📊 Response Status: {response.status_code}")
        print(f"📋 Response Headers: {dict(response.headers)}")
        
        if response.ok:
            print("✅ GetEnvVariables endpoint responded successfully!")
            try:
                # Try to parse as JSON if possible
                json_data = response.json()
                print(f"📄 Response JSON: {json_data}")
            except:
                # If not JSON, print raw text
                print(f"📄 Response Text: {response.text}")
        else:
            print(f"❌ GetEnvVariables endpoint failed with status {response.status_code}")
            print(f"📄 Response Text: {response.text}")
            
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
    print("🚀 Running container assignment examples...")
    print("\nChoose an option:")
    print("1. Assign existing container (http://localhost:8080)")
    print("2. Spawn new container automatically")
    
    choice = input("\nEnter choice (1 or 2): ").strip()
    
    try:
        if choice == "1":
            print("\n☕ Assigning existing Java container...")
            controller = assign_java_container_existing()
            test_get_env_variables_endpoint(controller)
            
        elif choice == "2":
            print("\n☕ Spawning and assigning new Java container...")
            assign_java_container_with_spawn()
            # Note: Container is automatically cleaned up when exiting the 'with' block
            print("✅ Container has been cleaned up")
            
        else:
            print("❌ Invalid choice. Please enter 1 or 2.")
            exit(1)
        
    except Exception as e:
        print(f"❌ Error during assignment: {e}")
        import traceback
        traceback.print_exc()