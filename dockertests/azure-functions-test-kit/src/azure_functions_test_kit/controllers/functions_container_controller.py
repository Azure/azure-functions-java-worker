# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Generic container assignment utility for Azure Functions containers.
Can be used with any runtime (Python, Java, .NET, etc.)
"""
import base64
import json
import os
import re
import secrets
import subprocess
import sys
import time
import uuid
from typing import Dict, Optional

import requests
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives import padding

# Docker constants
_DOCKER_PATH = "DOCKER_PATH"
_DOCKER_DEFAULT_PATH = "docker"
_MESH_IMAGE_URL = "https://mcr.microsoft.com/v2/azure-functions/mesh/tags/list"
_MESH_IMAGE_REPO = "mcr.microsoft.com/azure-functions/mesh"
_CUSTOM_IMAGE = "CUSTOM_IMAGE"


class FunctionsContainerController:
    """A generic controller for assigning specialization context to any
    Azure Functions container regardless of runtime.
    """

    _docker_cmd = os.getenv(_DOCKER_PATH, _DOCKER_DEFAULT_PATH)
    _ports: Dict[str, str] = {}  # { container_name: port }
    _mesh_images: Dict[str, str] = {}  # { host version: image tag }
    
    # Generate shared encryption keys once for all instances
    _container_encryption_key: str = base64.b64encode(secrets.token_bytes(32)).decode('utf-8')
    _azure_web_encryption_key: str = secrets.token_bytes(24).hex().upper()

    def __init__(self, container_url: str = None, runtime: str = "python", 
                 runtime_version: str = "3.9", site_name: Optional[str] = None,
                 host_version: str = "4", worker_directory: Optional[str] = None,
                 docker_flags: list = None, override_docker_flags: bool = False):
        """Initialize the controller.
        
        Args:
            container_url: The URL of the running container (e.g., http://localhost:8080)
                          If None, will spawn a new container
            runtime: The runtime type (python, java, dotnet, node, etc.)
            runtime_version: The version of the runtime
            site_name: Optional site name, will generate UUID if not provided
            host_version: Azure Functions host version (default: "4")
            worker_directory: Optional path to custom worker directory to mount.
                            If provided, mounts to /azure-functions-host/workers/{runtime}
            docker_flags: Additional Docker flags to append or override defaults
                         Format: list of strings like ["--cap-add", "NET_ADMIN", "-e", "FOO=bar"]
            override_docker_flags: If True, replace default flags with docker_flags.
                                  If False (default), append docker_flags to defaults.
        """
        self._runtime = runtime.lower()
        self._runtime_version = runtime_version
        self._site_name = site_name or str(uuid.uuid4())
        self._host_version = host_version
        self._container_url = container_url.rstrip('/') if container_url else None
        self._container_name = None
        self._worker_directory = worker_directory
        self._docker_flags = docker_flags or []
        self._override_docker_flags = override_docker_flags
        
    @property
    def url(self) -> str:
        if self._container_url:
            return self._container_url
        
        if self._container_name and self._container_name in self._ports:
            return f'http://localhost:{self._ports[self._container_name]}'
        
        raise RuntimeError('Container not initialized. Either provide container_url or call spawn_container()')

    def spawn_container(self, image: str = None, env: Dict[str, str] = {}) -> int:
        """Create a docker container and record its port.
        
        Args:
            image: Docker image to use. If None, will find latest mesh image for runtime
            env: Additional environment variables to set in the container
            
        Returns:
            The port number the container is listening on
        """
        if self._container_url:
            raise RuntimeError('Container URL already set. Cannot spawn a new container.')
        
        # Use custom image or find latest mesh image
        if image is None:
            image = (os.environ.get(_CUSTOM_IMAGE) or 
                    self._find_latest_mesh_image(self._host_version, self._runtime_version))
        
        print(f"🐳 Using Docker image: {image}")
        
        # Use site name as container name
        self._container_name = self._site_name
        
        # Construct docker run command
        run_cmd = [self._docker_cmd, "run"]
        
        if self._override_docker_flags:
            # Use only custom flags
            run_cmd.extend(self._docker_flags)
        else:
            # Start with default flags
            run_cmd.extend(["-p", "0:80", "-d"])
            run_cmd.extend(["--name", self._container_name, "--privileged"])
            run_cmd.extend(["--cap-add", "SYS_ADMIN"])
            run_cmd.extend(["--device", "/dev/fuse"])
            
            # Mount worker directory if provided
            if self._worker_directory:
                host_worker_path = f"/azure-functions-host/workers/{self._runtime}"
                run_cmd.extend(["-v", f"{self._worker_directory}:{host_worker_path}"])
                print(f"📦 Mounting worker: {self._worker_directory} -> {host_worker_path}")
            else:
                print(f"⚠️  No worker directory provided, using built-in {self._runtime} worker from image")
            
            # Append custom flags
            run_cmd.extend(self._docker_flags)
            
            # Add required environment variables
            run_cmd.extend(["-e", f"CONTAINER_NAME={self._container_name}"])
            run_cmd.extend(["-e", f"CONTAINER_ENCRYPTION_KEY={self._container_encryption_key}"])
            run_cmd.extend(["-e", "WEBSITE_PLACEHOLDER_MODE=1"])
            run_cmd.extend(["-e", f"WEBSITE_SITE_NAME={self._site_name}"])
            run_cmd.extend(["-e", "WEBSITE_SKU=Dynamic"])
            
            # Add AzureWebJobsStorage if available
            storage = os.getenv('AzureWebJobsStorage')
            if storage:
                run_cmd.extend(["-e", f"AzureWebJobsStorage={storage}"])
            
            # Add additional environment variables
            for key, value in env.items():
                run_cmd.extend(["-e", f"{key}={value}"])
        
        run_cmd.append(image)
        
        print(f"🚀 Spawning container: {' '.join(run_cmd)}")
        
        run_process = subprocess.run(args=run_cmd,
                                     stdout=subprocess.PIPE,
                                     stderr=subprocess.PIPE)

        if run_process.returncode != 0:
            raise RuntimeError(f'Failed to spawn docker container for {image} '
                               f'with name {self._container_name}. '
                               f'stderr: {run_process.stderr.decode()}')

        # Wait for the port to be exposed
        time.sleep(3)

        # Acquire the port number of the container
        port_cmd = [self._docker_cmd, "port", self._container_name]
        port_process = subprocess.run(args=port_cmd,
                                      stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE)
        if port_process.returncode != 0:
            raise RuntimeError(f'Failed to acquire port for {self._container_name}. '
                               f'stderr: {port_process.stderr.decode()}')
        
        port_number = port_process.stdout.decode().strip('\n').split(':')[-1]

        # Register port number
        self._ports[self._container_name] = port_number
        
        print(f"✅ Container spawned on port {port_number}")

        # Wait for container to be ready
        time.sleep(6)
        return int(port_number)

    def get_container_logs(self) -> str:
        """Get container logs (stdout and stderr merged)"""
        if not self._container_name:
            raise RuntimeError('No container spawned. Call spawn_container() first.')
        
        get_logs_cmd = [self._docker_cmd, "logs", self._container_name]
        get_logs_process = subprocess.run(args=get_logs_cmd,
                                          stdout=subprocess.PIPE,
                                          stderr=subprocess.PIPE)

        # The `docker logs` command merges stdout and stderr into stdout
        return get_logs_process.stdout.decode('utf-8')

    def safe_kill_container(self) -> bool:
        """Kill a container by its name. Returns True on success."""
        if not self._container_name:
            return False
        
        kill_cmd = [self._docker_cmd, "rm", "-f", self._container_name]
        kill_process = subprocess.run(args=kill_cmd, stdout=subprocess.DEVNULL)
        exit_code = kill_process.returncode

        if self._container_name in self._ports:
            del self._ports[self._container_name]
        
        if exit_code == 0:
            print(f"🗑️ Container {self._container_name} removed")
        
        return exit_code == 0

    def _find_latest_mesh_image(self, host_major: str, runtime_version: str) -> str:
        """
        Return the tag for the most recent mesh image that starts with
        <host_major> and matches the runtime.
        
        Example tag for Java: 4.1040.100-0-java21
        Example tag for Python: 4.1040.100-0-python3.13
        """
        cache_key = f"{host_major}-{self._runtime}-{runtime_version}"
        if cache_key in self._mesh_images:
            return self._mesh_images[cache_key]

        # Determine the runtime suffix based on runtime type
        runtime_suffix = f"{self._runtime}{runtime_version}"
        
        # Pattern to match: host_major.x.x-x-runtime_suffix
        tag_pattern = re.compile(
            rf'{re.escape(host_major)}\.\d+\.\d+-\d+-{re.escape(runtime_suffix)}$'
        )

        response = requests.get(_MESH_IMAGE_URL, timeout=10)
        if not response.ok:
            raise RuntimeError(
                f'Failed to query latest image for v{host_major} {runtime_suffix}. '
                f'Status {response.status_code}'
            )

        # Strip "-upgrade" from any temporary tags
        tags = [t.removesuffix("-upgrade") for t in response.json().get("tags", [])]

        # Keep only tags that match host_major and runtime
        candidates = [t for t in tags if tag_pattern.match(t)]
        if not candidates:
            raise RuntimeError(
                f'No mesh image found for v{host_major} {runtime_suffix}.'
            )

        def _numeric_key(tag: str) -> tuple:
            """Convert the version part to a tuple of integers for numeric comparison."""
            numeric_part = tag.split(f"-{runtime_suffix}")[0].replace("-", ".")
            return tuple(int(piece) for piece in numeric_part.split("."))

        latest_tag = max(candidates, key=_numeric_key)
        image_tag = f"{_MESH_IMAGE_REPO}:{latest_tag}"
        self._mesh_images[cache_key] = image_tag
        
        print(f"📦 Found latest mesh image: {image_tag}")
        return image_tag

    def assign_container(self, env: Dict[str, str] = {}):
        """Make a POST request to /admin/instance/assign to specialize the
        container with the given environment variables.
        
        Args:
            env: Environment variables to set in the container
        """
        # Add compulsory fields in specialization context
        env["FUNCTIONS_EXTENSION_VERSION"] = f"~{self._host_version}"
        env["FUNCTIONS_WORKER_RUNTIME"] = self._runtime
        env["FUNCTIONS_WORKER_RUNTIME_VERSION"] = self._runtime_version
        env["WEBSITE_SITE_NAME"] = self._site_name
        env["WEBSITE_HOSTNAME"] = f"{self._site_name}.azurewebsites.com"
        env["AzureWebEncryptionKey"] = self._azure_web_encryption_key

        # Debug: Print key environment variables
        print(f"🔍 DEBUG: Runtime: {self._runtime}")
        print(f"🔍 DEBUG: Runtime Version: {self._runtime_version}")
        print(f"🔍 DEBUG: Site Name: {self._site_name}")
        
        scm_package = env.get("SCM_RUN_FROM_PACKAGE", "NOT_SET")
        print(f"🔍 DEBUG: SCM_RUN_FROM_PACKAGE: {scm_package}")

        # Wait for the container to be ready
        print("⏳ Waiting for container to be ready...")
        max_retries = 360
        for i in range(max_retries):
            try:
                ping_req = requests.Request(method="GET", url=f"{self.url}/admin/host/ping")
                ping_response = self.send_request(ping_req)
                if ping_response.ok:
                    print(f"✅ Container ready after {i + 1} attempts")
                    break
                else:
                    print(f"🔍 DEBUG: Ping attempt {i+1}/{max_retries} failed with status "
                          f"{ping_response.status_code}")
            except Exception as e:
                print(f"🔍 DEBUG: Ping attempt {i + 1}/{max_retries} failed with exception: {e}")
            time.sleep(1)
        else:
            raise RuntimeError(f'Container at {self.url} did not become ready in time')

        # Send the specialization context via a POST request
        print("📤 Sending specialization context...")
        print(env)
        req = requests.Request(
            method="POST",
            url=f"{self.url}/admin/instance/assign",
            data=json.dumps({
                "encryptedContext": self._get_site_encrypted_context(
                    self._site_name, env
                )
            })
        )
        response = self.send_request(req)
        if not response.ok:
            raise RuntimeError(f'Failed to specialize container at {self.url}'
                               f' (status {response.status_code}).'
                               f' Response: {response.text}')
        
        print("✅ Container assignment successful!")
        return response

    def wait_for_host_running(self, timeout: int = 120, check_interval: int = 5) -> bool:
        """Wait for the Azure Functions host to reach 'Running' state.
        
        This method polls the /admin/host/status endpoint until the host reports
        a 'Running' state or the timeout is reached. Use this after assign_container()
        when you want to verify the host is running, even if no functions are loaded.
        
        Args:
            timeout: Maximum time to wait in seconds (default: 120)
            check_interval: Time between checks in seconds (default: 5)
            
        Returns:
            bool: True if host is running, False if timeout reached
            
        Raises:
            RuntimeError: If container is not running or URL not available
        """
        if not self.url:
            raise RuntimeError("Container URL not available. Spawn or assign container first.")
        
        print(f"⏳ Waiting for host to reach 'Running' state (timeout: {timeout}s)...")
        start_time = time.time()
        last_error = None
        last_state = None
        
        while time.time() - start_time < timeout:
            try:
                # Check host status
                # Use post_assignment=True since this check happens after specialization
                req = requests.Request('GET', f'{self.url}/admin/host/status')
                response = self.send_request(req, post_assignment=True)
                
                if response.ok:
                    status = response.json()
                    state = status.get('state', 'Unknown')
                    last_state = state
                    
                    if state == 'Running':
                        elapsed = time.time() - start_time
                        print(f"✅ Host is running (took {elapsed:.1f}s)")
                        return True
                    else:
                        print(f"   Host state: {state} (waiting...)")
                else:
                    print(f"   Host status endpoint returned: {response.status_code} (waiting...)")
                        
            except Exception as e:
                last_error = str(e)
                # Continue waiting on errors (container might still be initializing)
            
            time.sleep(check_interval)
        
        # Timeout reached
        elapsed = time.time() - start_time
        print(f"❌ Host not running after {elapsed:.1f}s")
        if last_state:
            print(f"   Last state: {last_state}")
        if last_error:
            print(f"   Last error: {last_error}")
        return False

    def wait_for_functions_loaded(self, timeout: int = 120, check_interval: int = 5) -> bool:
        """Wait for functions to be loaded and available.
        
        This method polls the /admin/functions endpoint until functions are loaded
        or the timeout is reached. Use this after assign_container() to ensure
        the container has completed specialization and loaded all functions.
        
        Args:
            timeout: Maximum time to wait in seconds (default: 120)
            check_interval: Time between checks in seconds (default: 5)
            
        Returns:
            bool: True if functions are loaded, False if timeout reached
            
        Raises:
            RuntimeError: If container is not running or URL not available
        """
        if not self.url:
            raise RuntimeError("Container URL not available. Spawn or assign container first.")
        
        print(f"⏳ Waiting for functions to be loaded (timeout: {timeout}s)...")
        start_time = time.time()
        last_error = None
        last_status = None
        
        while time.time() - start_time < timeout:
            try:
                # Check if functions are loaded
                # Use post_assignment=True since this check happens after specialization
                req = requests.Request('GET', f'{self.url}/admin/functions')
                response = self.send_request(req, post_assignment=True)
                
                if response.ok:
                    functions = response.json()
                    if functions and len(functions) > 0:
                        elapsed = time.time() - start_time
                        print(f"✅ Functions loaded and ready (found {len(functions)} function(s), took {elapsed:.1f}s)")
                        return True
                    else:
                        print(f"   No functions loaded yet (waiting...)")
                else:
                    last_status = response.status_code
                    print(f"   Functions endpoint status: {response.status_code} (waiting...)")
                        
            except Exception as e:
                last_error = str(e)
                # Continue waiting on errors (container might still be initializing)
            
            time.sleep(check_interval)
        
        # Timeout reached
        elapsed = time.time() - start_time
        print(f"❌ Functions not ready after {elapsed:.1f}s")
        if last_status:
            print(f"   Last status code: {last_status}")
        if last_error:
            print(f"   Last error: {last_error}")
        return False

    def send_request(
            self,
            req: requests.Request,
            ses: requests.Session = None,
            post_assignment: bool = False
    ) -> requests.Response:
        """Send a request with authorization token. Return a Response object
        
        Args:
            req: The request to send
            ses: Optional session to use
            post_assignment: If True, use full URL format for JWT audience (for post-specialization calls)
                            If False, use container name format (for pre-specialization calls like ping/assign)
        """
        session = ses
        if session is None:
            session = requests.Session()

        prepped = session.prepare_request(req)
        prepped.headers['Content-Type'] = 'application/json'

        # Generate token and set headers exactly like the original implementation
        try:
            jwt_token = self._generate_jwt_token(post_assignment=post_assignment)
            # Use JWT token for newer Azure Functions host versions
            prepped.headers['Authorization'] = f'Bearer {jwt_token}'
        except ImportError:
            # Fall back to the old SWT token format if jwt library is not available
            swt_token = self._get_site_restricted_token()
            prepped.headers['x-ms-site-restricted-token'] = swt_token
            prepped.headers['Authorization'] = f'Bearer {swt_token}'

        # Add additional headers required by Azure Functions host (use site_name instead of uuid)
        prepped.headers['x-site-deployment-id'] = self._site_name
        prepped.headers['x-ms-client-request-id'] = str(uuid.uuid4())
        prepped.headers['x-ms-request-id'] = str(uuid.uuid4())

        resp = session.send(prepped)
        return resp

    @classmethod
    def _get_site_restricted_token(cls) -> str:
        """Get the header value which can be used by x-ms-site-restricted-token
        which expires in one day.
        """
        # For compatibility with older Azure Functions host versions,
        # try the old SWT format first
        exp_ns = int((time.time() + 24 * 60 * 60) * 1000000000)
        token = cls._encrypt_context(cls._container_encryption_key, f'exp={exp_ns}')
        return token

    def _generate_jwt_token(self, post_assignment: bool = False) -> str:
        """Generate a proper JWT token for newer Azure Functions host versions.
        
        Args:
            post_assignment: If True, use full URL format for audience (post-specialization)
                            If False, use container name format (pre-specialization)
        """
        try:
            import jwt
        except ImportError:
            # Fall back to SWT format if JWT library not available
            return self._get_site_restricted_token()

        # JWT payload matching Azure Functions host expectations
        exp_time = int(time.time()) + (24 * 60 * 60)  # 24 hours from now

        # Use the site name consistently
        site_name = self._site_name

        # Issuer always uses the full URL format
        issuer = f"https://{site_name}.azurewebsites.net"

        # Audience format changes based on whether host is in placeholder mode or specialized
        if post_assignment:
            # After specialization: audience should match issuer format
            audience = issuer
        else:
            # Before/during specialization: audience is the container name
            audience = site_name

        payload = {
            'exp': exp_time,
            'iat': int(time.time()),
            'iss': issuer,
            'aud': audience
        }

        # Use the same encryption key for JWT signing
        key = base64.b64decode(self._container_encryption_key.encode())

        # Generate JWT token using HMAC SHA256 (matches Azure Functions host)
        jwt_token = jwt.encode(payload, key, algorithm='HS256')
        return jwt_token

    @classmethod
    def _get_site_encrypted_context(cls,
                                    site_name: str,
                                    env: Dict[str, str]) -> str:
        """Get the encrypted context for placeholder mode specialization
        
        Args:
            site_name: The site name for the container
            env: Environment variables
        """
        # Ensure WEBSITE_SITE_NAME is set to simulate production mode
        env["WEBSITE_SITE_NAME"] = site_name

        ctx = {
            "SiteId": 1,
            "SiteName": site_name,
            "Environment": env
        }

        json_ctx = json.dumps(ctx)

        encrypted = cls._encrypt_context(cls._container_encryption_key, json_ctx)
        return encrypted

    @classmethod
    def _encrypt_context(cls, encryption_key: str, plain_text: str) -> str:
        """Encrypt plain text context into an encrypted message which can
        be accepted by the host
        """
        # Decode the encryption key
        encryption_key_bytes = base64.b64decode(encryption_key.encode())

        # Pad the plaintext to be a multiple of the AES block size
        padder = padding.PKCS7(algorithms.AES.block_size).padder()
        plain_text_bytes = padder.update(plain_text.encode()) + padder.finalize()

        # Initialization vector (IV) (fixed value for simplicity)
        iv_bytes = '0123456789abcedf'.encode()

        # Create AES cipher with CBC mode
        cipher = Cipher(algorithms.AES(encryption_key_bytes),
                        modes.CBC(iv_bytes), backend=default_backend())

        # Perform encryption
        encryptor = cipher.encryptor()
        encrypted_bytes = encryptor.update(plain_text_bytes) + encryptor.finalize()

        # Compute SHA256 hash of the encryption key
        digest = hashes.Hash(hashes.SHA256(), backend=default_backend())
        digest.update(encryption_key_bytes)
        key_sha256 = digest.finalize()

        # Encode IV, encrypted message, and SHA256 hash in base64
        iv_base64 = base64.b64encode(iv_bytes).decode()
        encrypted_base64 = base64.b64encode(encrypted_bytes).decode()
        key_sha256_base64 = base64.b64encode(key_sha256).decode()

        # Return the final result
        return f'{iv_base64}.{encrypted_base64}.{key_sha256_base64}'

    def __enter__(self):
        """Context manager entry - spawn container if not already running"""
        if not self._container_url and not self._container_name:
            self.spawn_container()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        """Context manager exit - cleanup container"""
        if self._container_name:
            logs = self.get_container_logs()
            self.safe_kill_container()
            
            if traceback:
                print(f'❌ Test failed with container logs:\n{logs}',
                      file=sys.stderr,
                      flush=True)
