# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Azurite container controller for Azure Storage emulation.
"""
import base64
import secrets
import subprocess
import time
from typing import Optional


class AzuriteContainerController:
    """Controller for managing Azurite storage emulator container."""
    
    def __init__(self, container_name: str = "azurite-test", 
                 docker_cmd: str = "docker",
                 blob_port: int = 0,
                 queue_port: int = 0,
                 table_port: int = 0,
                 account_name: str = "devstoreaccount1",
                 account_key: Optional[str] = None):
        """Initialize the Azurite controller.
        
        Args:
            container_name: Name for the Azurite container
            docker_cmd: Docker command to use
            blob_port: Port for blob service (0 = auto-assign)
            queue_port: Port for queue service (0 = auto-assign)
            table_port: Port for table service (0 = auto-assign)
            account_name: Custom storage account name (default: devstoreaccount1)
            account_key: Custom account key as base64 string (default: Azurite default key)
                        If None, generates a random 512-bit key
        """
        self._container_name = container_name
        self._docker_cmd = docker_cmd
        self._requested_blob_port = blob_port
        self._requested_queue_port = queue_port
        self._requested_table_port = table_port
        self._actual_blob_port: Optional[int] = None
        self._actual_queue_port: Optional[int] = None
        self._actual_table_port: Optional[int] = None
        self._is_running = False
        
        # Set account name and generate key if not provided
        self._account_name = account_name
        self._account_key = account_key or base64.b64encode(secrets.token_bytes(64)).decode('utf-8')
        
    @property
    def connection_string(self) -> str:
        """Get the connection string for local access."""
        if not self._is_running or self._actual_blob_port is None:
            raise RuntimeError("Azurite container not running. Call spawn_container() first.")
        
        return (
            f"DefaultEndpointsProtocol=http;"
            f"AccountName={self._account_name};"
            f"AccountKey={self._account_key};"
            f"BlobEndpoint=http://127.0.0.1:{self._actual_blob_port}/{self._account_name};"
            f"QueueEndpoint=http://127.0.0.1:{self._actual_queue_port}/{self._account_name};"
            f"TableEndpoint=http://127.0.0.1:{self._actual_table_port}/{self._account_name};"
        )
    
    @property
    def docker_connection_string(self) -> str:
        """Get the connection string for Docker container access."""
        if not self._is_running or self._actual_blob_port is None:
            raise RuntimeError("Azurite container not running. Call spawn_container() first.")
        
        # Use Docker bridge network IP (172.17.0.1 on Linux, host.docker.internal on Windows)
        # For Windows/WSL, try bridge IP first as it's more reliable
        return (
            f"DefaultEndpointsProtocol=http;"
            f"AccountName={self._account_name};"
            f"AccountKey={self._account_key};"
            f"BlobEndpoint=http://172.17.0.1:{self._actual_blob_port}/{self._account_name};"
            f"QueueEndpoint=http://172.17.0.1:{self._actual_queue_port}/{self._account_name};"
            f"TableEndpoint=http://172.17.0.1:{self._actual_table_port}/{self._account_name};"
        )
    
    @property
    def blob_endpoint(self) -> str:
        """Get the blob endpoint URL."""
        if not self._is_running or self._actual_blob_port is None:
            raise RuntimeError("Azurite container not running. Call spawn_container() first.")
        return f"http://127.0.0.1:{self._actual_blob_port}/{self._account_name}"
    
    @property
    def is_running(self) -> bool:
        """Check if the Azurite container is running."""
        return self._is_running
    
    def spawn_container(self) -> None:
        """Start the Azurite container."""
        if self._is_running:
            print(f"⚠️  Azurite container '{self._container_name}' is already running")
            return
        
        # Check if container already exists
        check_cmd = [self._docker_cmd, "ps", "-a", "--filter", f"name={self._container_name}", "--format", "{{.Names}}"]
        check_process = subprocess.run(check_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        if self._container_name in check_process.stdout.decode():
            # Container exists, remove it first to ensure clean state
            print(f"🔄 Removing existing Azurite container '{self._container_name}'...")
            subprocess.run([self._docker_cmd, "rm", "-f", self._container_name], stdout=subprocess.DEVNULL)
        
        print(f"🚀 Spawning Azurite container '{self._container_name}'...")
        
        # Use requested ports or 0 for auto-assignment
        blob_port_arg = f"{self._requested_blob_port}:10000" if self._requested_blob_port else "0:10000"
        queue_port_arg = f"{self._requested_queue_port}:10001" if self._requested_queue_port else "0:10001"
        table_port_arg = f"{self._requested_table_port}:10002" if self._requested_table_port else "0:10002"
        
        run_cmd = [
            self._docker_cmd, "run", "-d",
            "--name", self._container_name,
            "-p", blob_port_arg,
            "-p", queue_port_arg,
            "-p", table_port_arg,
            "-e", f"AZURITE_ACCOUNTS={self._account_name}:{self._account_key}",
            "mcr.microsoft.com/azure-storage/azurite"
        ]
        
        print(f"🔑 Using account: {self._account_name}")
        
        run_process = subprocess.run(run_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        if run_process.returncode != 0:
            raise RuntimeError(f"Failed to start Azurite container: {run_process.stderr.decode()}")
        
        # Wait for container to start
        time.sleep(2)
        
        # Get the actual assigned ports
        self._actual_blob_port = self._get_container_port("10000/tcp")
        self._actual_queue_port = self._get_container_port("10001/tcp")
        self._actual_table_port = self._get_container_port("10002/tcp")
        
        # Wait for Azurite to be ready
        print("⏳ Waiting for Azurite to be ready...")
        time.sleep(3)
        
        self._is_running = True
        print(f"✅ Azurite container running on ports {self._actual_blob_port}/{self._actual_queue_port}/{self._actual_table_port}")
    
    def _get_container_port(self, internal_port: str) -> int:
        """Get the externally mapped port for a container's internal port.
        
        Args:
            internal_port: Internal port (e.g., "10000/tcp")
            
        Returns:
            External port number
        """
        port_cmd = [self._docker_cmd, "port", self._container_name, internal_port]
        port_process = subprocess.run(port_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        
        if port_process.returncode != 0:
            raise RuntimeError(f"Failed to get port for {internal_port}: {port_process.stderr.decode()}")
        
        # Output format: "0.0.0.0:12345" or "127.0.0.1:12345"
        output = port_process.stdout.decode().strip()
        port_number = output.split(':')[-1]
        return int(port_number)
    
    def safe_kill_container(self) -> bool:
        """Stop and remove the Azurite container."""
        if not self._is_running:
            return False
        
        print(f"🗑️  Stopping Azurite container '{self._container_name}'...")
        kill_cmd = [self._docker_cmd, "rm", "-f", self._container_name]
        kill_process = subprocess.run(kill_cmd, stdout=subprocess.DEVNULL)
        
        self._is_running = False
        self._actual_blob_port = None
        self._actual_queue_port = None
        self._actual_table_port = None
        
        if kill_process.returncode == 0:
            print(f"✅ Azurite container '{self._container_name}' removed")
            return True
        return False
    
    def __enter__(self):
        """Context manager entry - start Azurite."""
        self.spawn_container()
        return self
    
    def __exit__(self, exc_type, exc_value, traceback):
        """Context manager exit - stop Azurite."""
        self.safe_kill_container()
