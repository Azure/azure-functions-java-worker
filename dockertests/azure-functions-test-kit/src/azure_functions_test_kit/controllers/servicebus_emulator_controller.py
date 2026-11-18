# Copyright (c) Microsoft Corporation. All rights reserved.
# Licensed under the MIT License.
"""
Azure Service Bus emulator controller.

Runs the Service Bus emulator and its SQL Server companion on a dedicated docker network, so the
Functions container reaches the broker by name.
"""
import json
import os
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
from typing import Optional

EMULATOR_IMAGE = "mcr.microsoft.com/azure-messaging/servicebus-emulator:2.0.1"
SQL_IMAGE = "mcr.microsoft.com/mssql/server:2022-latest"
ACCEPT_EULA_ENV = "SERVICEBUS_EMULATOR_ACCEPT_EULA"

_CONFIG_PATH = "/ServiceBus_Emulator/ConfigFiles/Config.json"
_AMQP_PORT = 5672
_HEALTH_PORT = 5300
_SQL_ALIAS = "servicebus-sql"
_EMULATOR_ALIAS = "servicebus-emulator"
_SAS_KEY_NAME = "RootManageSharedAccessKey"
_SAS_KEY = "SAS_KEY_VALUE"


class ServiceBusEmulatorController:
    """Controller for the Service Bus emulator, its SQL Server and the docker network between them."""

    def __init__(self,
                 config: dict,
                 environment_id: Optional[str] = None,
                 docker_cmd: str = "docker",
                 emulator_image: Optional[str] = None,
                 sql_image: Optional[str] = None,
                 startup_timeout: int = 240,
                 sql_memory_limit_mb: int = 1024):
        """Initialize the controller.

        Args:
            config: Emulator Config.json content, a dict with the top level "UserConfig" key.
            environment_id: Suffix for the container and network names.
            docker_cmd: Docker command to use.
            emulator_image: Emulator image, defaults to EMULATOR_IMAGE.
            sql_image: SQL Server image, defaults to SQL_IMAGE.
            startup_timeout: Seconds to wait for the emulator health endpoint.
            sql_memory_limit_mb: Memory limit passed to SQL Server.
        """
        self._config = config
        self._id = environment_id or secrets.token_hex(8)
        self._docker_cmd = docker_cmd
        self._emulator_image = emulator_image or os.getenv("SERVICEBUS_EMULATOR_IMAGE", EMULATOR_IMAGE)
        self._sql_image = sql_image or os.getenv("SERVICEBUS_EMULATOR_SQL_IMAGE", SQL_IMAGE)
        self._startup_timeout = startup_timeout
        self._sql_memory_limit_mb = sql_memory_limit_mb
        self._sql_password = f"Sb1!{secrets.token_urlsafe(12)}"
        self._amqp_port: Optional[int] = None
        self._health_port: Optional[int] = None
        self._created = False
        self._is_running = False

    @property
    def network_name(self) -> str:
        return f"servicebus-net-{self._id}"

    @property
    def emulator_container_name(self) -> str:
        return f"servicebus-emulator-{self._id}"

    @property
    def sql_container_name(self) -> str:
        return f"servicebus-sql-{self._id}"

    @property
    def is_running(self) -> bool:
        return self._is_running

    @property
    def connection_string(self) -> str:
        """Connection string for clients on the docker host."""
        if self._amqp_port is None:
            raise RuntimeError("Service Bus emulator not running. Call spawn_container() first.")
        return (f"Endpoint=sb://localhost:{self._amqp_port};SharedAccessKeyName={_SAS_KEY_NAME};"
                f"SharedAccessKey={_SAS_KEY};UseDevelopmentEmulator=true;")

    @property
    def docker_connection_string(self) -> str:
        """Connection string for containers attached to the emulator network."""
        return (f"Endpoint=sb://{_EMULATOR_ALIAS};SharedAccessKeyName={_SAS_KEY_NAME};"
                f"SharedAccessKey={_SAS_KEY};UseDevelopmentEmulator=true;")

    def _docker(self, *args: str, check: bool = True) -> subprocess.CompletedProcess:
        result = subprocess.run([self._docker_cmd, *args], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if check and result.returncode != 0:
            raise RuntimeError(f"docker {' '.join(args[:2])} failed: {result.stderr.decode(errors='replace')}")
        return result

    def _require_eula(self) -> None:
        # The SQL Server and Service Bus emulator images only run when their EULAs are accepted.
        if os.getenv(ACCEPT_EULA_ENV, "").strip().upper() != "Y":
            raise RuntimeError(
                f"The Service Bus emulator and SQL Server images require accepting their license terms. "
                f"Review them, then set {ACCEPT_EULA_ENV}=Y to run this test. "
                "See https://learn.microsoft.com/azure/service-bus-messaging/overview-emulator")

    def spawn_container(self) -> None:
        """Create the network, start SQL Server and the emulator, and wait until the emulator is healthy."""
        if self._is_running:
            return
        self._require_eula()

        print(f"Creating Service Bus emulator network '{self.network_name}'...")
        self._created = True
        self._docker("network", "create", self.network_name)

        print(f"Starting SQL Server container '{self.sql_container_name}'...")
        self._docker(
            "run", "-d", "--name", self.sql_container_name,
            "--network", self.network_name, "--network-alias", _SQL_ALIAS,
            "-e", "ACCEPT_EULA=Y",
            "-e", f"MSSQL_SA_PASSWORD={self._sql_password}",
            "-e", f"MSSQL_MEMORY_LIMIT_MB={self._sql_memory_limit_mb}",
            self._sql_image)

        print(f"Starting Service Bus emulator container '{self.emulator_container_name}'...")
        self._docker(
            "create", "--name", self.emulator_container_name,
            "--network", self.network_name, "--network-alias", _EMULATOR_ALIAS,
            "-p", f"0:{_AMQP_PORT}", "-p", f"0:{_HEALTH_PORT}",
            "-e", "ACCEPT_EULA=Y",
            "-e", f"SQL_SERVER={_SQL_ALIAS}",
            "-e", f"MSSQL_SA_PASSWORD={self._sql_password}",
            self._emulator_image)
        self._copy_config()
        self._docker("start", self.emulator_container_name)

        self._amqp_port = self._get_container_port(f"{_AMQP_PORT}/tcp")
        self._health_port = self._get_container_port(f"{_HEALTH_PORT}/tcp")
        self._wait_for_health()
        self._is_running = True
        print(f"Service Bus emulator ready on localhost:{self._amqp_port}")

    def _copy_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            config_file = os.path.join(temp_dir, "Config.json")
            with open(config_file, "w", encoding="utf-8") as f:
                json.dump(self._config, f, indent=2)
            self._docker("cp", config_file, f"{self.emulator_container_name}:{_CONFIG_PATH}")

    def _get_container_port(self, internal_port: str) -> int:
        result = self._docker("port", self.emulator_container_name, internal_port)
        # Output has one "0.0.0.0:12345" line per address family
        return int(result.stdout.decode().splitlines()[0].rsplit(":", 1)[-1])

    def _wait_for_health(self) -> None:
        url = f"http://127.0.0.1:{self._health_port}/health"
        deadline = time.time() + self._startup_timeout
        while time.time() < deadline:
            try:
                with urllib.request.urlopen(url, timeout=5) as response:
                    if response.status == 200:
                        return
            except (urllib.error.URLError, ConnectionError, TimeoutError, OSError):
                pass
            time.sleep(3)
        logs = self._docker("logs", "--tail", "50", self.emulator_container_name, check=False)
        raise RuntimeError(
            f"Service Bus emulator did not become healthy within {self._startup_timeout}s.\n"
            f"{logs.stdout.decode(errors='replace')}{logs.stderr.decode(errors='replace')}")

    def connect_container(self, container_name: str) -> None:
        """Attach another container to the emulator network, keeping its existing networks."""
        self._docker("network", "connect", self.network_name, container_name)

    def get_container_logs(self) -> str:
        """Get the emulator logs, for diagnostics."""
        result = self._docker("logs", self.emulator_container_name, check=False)
        return result.stdout.decode(errors="replace") + result.stderr.decode(errors="replace")

    def safe_kill_container(self) -> bool:
        """Remove the emulator, SQL Server and network."""
        if not self._created:
            return False
        print(f"Removing Service Bus emulator resources for '{self._id}'...")
        self._docker("rm", "-f", "-v", self.emulator_container_name, check=False)
        self._docker("rm", "-f", "-v", self.sql_container_name, check=False)
        removed = self._docker("network", "rm", self.network_name, check=False)
        self._created = False
        self._is_running = False
        self._amqp_port = None
        self._health_port = None
        return removed.returncode == 0

    def __enter__(self):
        self.spawn_container()
        return self

    def __exit__(self, exc_type, exc_value, traceback):
        self.safe_kill_container()
