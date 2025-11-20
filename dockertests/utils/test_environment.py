# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Test environment manager for Azure Functions container testing.
Manages both Functions containers and storage (Azurite or real Azure Storage).
"""
import uuid
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional, Dict, List
from urllib.parse import quote

from azure.storage.blob import BlobServiceClient, generate_container_sas, ContainerSasPermissions

from .azurite_container_controller import AzuriteContainerController
from .functions_container_controller import FunctionsContainerController


class TestEnvironment:
    """Manages the complete test environment including storage and Functions containers."""
    
    # Supported app package extensions
    SUPPORTED_EXTENSIONS = ['.zip', '.squashfs']
    
    def __init__(
        self,
        use_azurite: bool = True,
        storage_connection_string: Optional[str] = None,
        apps_directory: str = "./apps",
        apps_to_upload: Optional[List[str]] = None,
        apps_container_name: str = "app",
        runtime: str = "java",
        runtime_version: str = "21",
        host_version: str = "4",
        site_name: Optional[str] = None,
        docker_flags: Optional[List[str]] = None,
        environment_id: Optional[str] = None
    ):
        """Initialize the test environment.
        
        Args:
            use_azurite: If True, use Azurite emulator. If False, use real Azure Storage
            storage_connection_string: Connection string for real Azure Storage (required if use_azurite=False)
            apps_directory: Directory containing app packages to upload
            apps_to_upload: Optional list of app names to upload (with or without extensions).
                          Examples: ['app1', 'app2.squashfs']
                          If specified without extension, will match any supported extension (.zip, .squashfs)
                          If None, all apps in apps_directory will be uploaded
            apps_container_name: Blob container name for storing app packages
            runtime: Functions runtime (java, python, dotnet, node, etc.)
            runtime_version: Runtime version
            host_version: Azure Functions host version
            site_name: Optional site name for the Functions container
            docker_flags: Additional Docker flags for the Functions container
            environment_id: Optional unique ID for this environment (auto-generated if not provided)
        """
        self.use_azurite = use_azurite
        self.apps_directory = Path(apps_directory)
        self.apps_to_upload = apps_to_upload  # List of specific files to upload, or None for all
        self.apps_container_name = apps_container_name
        self.runtime = runtime
        self.runtime_version = runtime_version
        self.host_version = host_version
        self.site_name = site_name
        self.docker_flags = docker_flags or []
        
        # Generate unique environment ID for this test environment
        self.environment_id = environment_id or str(uuid.uuid4())[:8]
        
        # Storage configuration
        if use_azurite:
            # Create unique Azurite container name using environment ID
            azurite_container_name = f"azurite-{self.environment_id}"
            self.azurite = AzuriteContainerController(
                container_name=azurite_container_name,
                blob_port=0,  # Auto-assign port
                queue_port=0,  # Auto-assign port
                table_port=0   # Auto-assign port
            )
            self._storage_connection_string = None
        else:
            if not storage_connection_string:
                raise ValueError("storage_connection_string is required when use_azurite=False")
            self.azurite = None
            self._storage_connection_string = storage_connection_string
        
        # Functions container controller
        self.functions_controller: Optional[FunctionsContainerController] = None
        
        # Storage client
        self._blob_service_client: Optional[BlobServiceClient] = None
        self._container_sas_token: Optional[str] = None
        self._uploaded_apps: Dict[str, str] = {}  # {blob_name_with_ext: blob_url}
    
    @property
    def storage_connection_string(self) -> str:
        """Get the storage connection string."""
        if self.use_azurite:
            return self.azurite.connection_string
        return self._storage_connection_string
    
    @property
    def docker_storage_connection_string(self) -> str:
        """Get the storage connection string for use from Docker containers."""
        if self.use_azurite:
            return self.azurite.docker_connection_string
        # For real Azure Storage, the connection string is the same
        return self._storage_connection_string
    
    @property
    def blob_service_client(self) -> BlobServiceClient:
        """Get or create the BlobServiceClient."""
        if self._blob_service_client is None:
            self._blob_service_client = BlobServiceClient.from_connection_string(
                self.storage_connection_string
            )
        return self._blob_service_client
    
    def start(self) -> 'TestEnvironment':
        """Start the test environment (storage and Functions container)."""
        print(f"🚀 Starting test environment '{self.environment_id}'...")
        
        # Start Azurite if needed
        if self.use_azurite:
            print("🐳 Starting Azurite emulator...")
            self.azurite.spawn_container()
            print(f"📡 Azurite connection string: {self.storage_connection_string[:100]}...")
        
        # Create blob container
        self._ensure_blob_container()
        
        # Generate container SAS token
        self._generate_container_sas()
        
        # Upload app packages
        self._upload_app_packages()
        
        # Initialize and spawn Functions container
        print("🐳 Starting Functions container...")
        functions_container_name = f"functions-{self.environment_id}"
        self.functions_controller = FunctionsContainerController(
            runtime=self.runtime,
            runtime_version=self.runtime_version,
            host_version=self.host_version,
            site_name=functions_container_name,
            docker_flags=self.docker_flags
        )
        self.functions_controller.spawn_container()
        
        print(f"✅ Test environment '{self.environment_id}' ready")
        return self
    
    def stop(self) -> None:
        """Stop the test environment and clean up resources."""
        print(f"🛑 Stopping test environment '{self.environment_id}'...")
        
        if self.functions_controller:
            self.functions_controller.safe_kill_container()
        
        if self.use_azurite and self.azurite:
            self.azurite.safe_kill_container()
        
        print(f"✅ Test environment '{self.environment_id}' stopped")
    
    def _ensure_blob_container(self) -> None:
        """Ensure the blob container exists."""
        try:
            container_client = self.blob_service_client.get_container_client(self.apps_container_name)
            if not container_client.exists():
                print(f"📦 Creating blob container '{self.apps_container_name}'...")
                container_client.create_container()
            else:
                print(f"📦 Using existing blob container '{self.apps_container_name}'")
        except Exception as e:
            raise RuntimeError(f"Failed to ensure blob container: {e}")
    
    def _generate_container_sas(self) -> None:
        """Generate a SAS token for the container."""
        try:
            # Get account name and key from connection string
            conn_parts = dict(part.split('=', 1) for part in self.storage_connection_string.split(';') if '=' in part)
            account_name = conn_parts.get('AccountName')
            account_key = conn_parts.get('AccountKey')
            
            if not account_name or not account_key:
                raise ValueError("Could not extract account name or key from connection string")
            
            # Generate SAS token valid for 7 days
            sas_token = generate_container_sas(
                account_name=account_name,
                container_name=self.apps_container_name,
                account_key=account_key,
                permission=ContainerSasPermissions(read=True, list=True),
                expiry=datetime.utcnow() + timedelta(days=7)
            )
            
            self._container_sas_token = sas_token
            print(f"🔑 Generated container SAS token (expires in 7 days)")
            
        except Exception as e:
            raise RuntimeError(f"Failed to generate SAS token: {e}")
    
    def _upload_app_packages(self) -> None:
        """Upload app packages from the apps directory to blob storage."""
        if not self.apps_directory.exists():
            print(f"⚠️  Apps directory '{self.apps_directory}' does not exist, skipping upload")
            return
        
        # Find app packages
        if self.apps_to_upload:
            # Upload only specified apps
            app_files = []
            for app_spec in self.apps_to_upload:
                # Check if app_spec already has an extension
                app_path = self.apps_directory / app_spec
                
                if app_path.exists():
                    # Exact match found
                    app_files.append(app_path)
                else:
                    # No exact match, try to find with any supported extension
                    found = False
                    for ext in self.SUPPORTED_EXTENSIONS:
                        app_path_with_ext = self.apps_directory / f"{app_spec}{ext}"
                        if app_path_with_ext.exists():
                            app_files.append(app_path_with_ext)
                            found = True
                            break
                    
                    if not found:
                        print(f"⚠️  Specified app '{app_spec}' (or with supported extensions) not found in '{self.apps_directory}'")
        else:
            # Upload all supported app packages - derive patterns from extensions
            app_files = []
            for ext in self.SUPPORTED_EXTENSIONS:
                pattern = f"*{ext}"
                app_files.extend(self.apps_directory.glob(pattern))
        
        if not app_files:
            if self.apps_to_upload:
                print(f"⚠️  None of the specified apps found in '{self.apps_directory}'")
            else:
                print(f"⚠️  No app packages found in '{self.apps_directory}'")
            return
        
        print(f"📤 Uploading {len(app_files)} app package(s)...")
        
        container_client = self.blob_service_client.get_container_client(self.apps_container_name)
        
        for app_file in app_files:
            blob_name = app_file.name
            blob_client = container_client.get_blob_client(blob_name)
            
            print(f"   📦 Uploading {blob_name}...")
            with open(app_file, 'rb') as data:
                blob_client.upload_blob(data, overwrite=True)
            
            # Generate SAS URL for this blob
            blob_url = self.get_blob_sas_url(blob_name, self.apps_container_name)
            self._uploaded_apps[blob_name] = blob_url  # Store with full name including extension
            print(f"   ✅ Uploaded: {blob_url}")
        
        print(f"✅ Uploaded {len(app_files)} app package(s)")
    
    def get_blob_sas_url(self, blob_name: str, container_name: Optional[str] = None) -> str:
        """Get a SAS URL for a specific blob.
        
        Args:
            blob_name: Name of the blob (with or without extension).
                      If specified without extension, will match any supported extension.
            container_name: Name of the container. If None, uses apps_container_name.
            
        Returns:
            Full URL with SAS token
            
        Raises:
            RuntimeError: If SAS token not generated or blob not found
        """
        if not self._container_sas_token:
            raise RuntimeError("SAS token not generated. Call start() first.")
        
        # Use apps container by default
        if container_name is None:
            container_name = self.apps_container_name
        
        # Try to find the actual blob name if extension not provided
        actual_blob_name = blob_name
        
        # Check if we need to search for the blob (no extension or not found directly)
        container_client = self.blob_service_client.get_container_client(container_name)
        
        try:
            # First try exact match
            blob_client = container_client.get_blob_client(blob_name)
            if not blob_client.exists():
                # Try to find with supported extensions
                found = False
                
                for ext in self.SUPPORTED_EXTENSIONS:
                    test_blob_name = f"{blob_name}{ext}"
                    test_blob_client = container_client.get_blob_client(test_blob_name)
                    if test_blob_client.exists():
                        actual_blob_name = test_blob_name
                        found = True
                        break
                
                if not found:
                    raise RuntimeError(f"Blob '{blob_name}' (or with supported extensions) not found in container")
        except Exception as e:
            if "not found" not in str(e).lower():
                # Re-raise if it's not a "not found" error
                raise
        
        if self.use_azurite:
            # For Azurite, use Docker bridge IP (172.17.0.1) with the dynamically assigned port
            blob_port = self.azurite._actual_blob_port
            base_url = f"http://172.17.0.1:{blob_port}/devstoreaccount1/{container_name}/{actual_blob_name}"
        else:
            # For real Azure Storage
            conn_parts = dict(part.split('=', 1) for part in self.storage_connection_string.split(';') if '=' in part)
            account_name = conn_parts.get('AccountName')
            base_url = f"https://{account_name}.blob.core.windows.net/{container_name}/{actual_blob_name}"
        
        return f"{base_url}?{self._container_sas_token}"
    
    def list_uploaded_apps(self) -> Dict[str, str]:
        """Get a dictionary of all uploaded apps and their URLs.
        
        Returns:
            Dictionary mapping app blob names (with extensions) to SAS URLs
        """
        return self._uploaded_apps.copy()
    
    def __enter__(self):
        """Context manager entry."""
        return self.start()
    
    def __exit__(self, exc_type, exc_value, traceback):
        """Context manager exit."""
        self.stop()
