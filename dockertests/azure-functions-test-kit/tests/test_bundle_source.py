"""Test bundle-source propagation at the Docker and specialization boundaries."""
import base64
import json
import subprocess
from pathlib import Path

import pytest
from cryptography.hazmat.primitives import padding
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

from azure_functions_test_kit import FunctionsContainerController, LinuxConsumptionTestEnvironment
from azure_functions_test_kit.controllers import functions_container_controller


BUNDLE_SOURCE = "FUNCTIONS_EXTENSIONBUNDLE_SOURCE_URI"
CONFIGURED_SOURCE = "https://cdn-staging.functions.azure.com/public"
OVERRIDE_SOURCE = "https://example.com/bundles"


@pytest.fixture
def docker_commands(monkeypatch):
    commands = []

    def run(args, **kwargs):
        env_files = []
        for index, token in enumerate(args):
            if token == "--env-file":
                env_files.append(Path(args[index + 1]))
            elif token.startswith("--env-file="):
                env_files.append(Path(token.split("=", 1)[1]))
        commands.append({
            "args": args,
            "env_files": env_files,
            "file_settings": [line for path in env_files for line in path.read_text().splitlines()],
        })
        output = b"container-id" if args[1] == "run" else b"0.0.0.0:12345\n"
        return subprocess.CompletedProcess(args, 0, stdout=output, stderr=b"")

    monkeypatch.setattr(functions_container_controller.subprocess, "run", run)
    monkeypatch.setattr(functions_container_controller.time, "sleep", lambda _: None)
    monkeypatch.setattr(FunctionsContainerController, "_ports", {})
    monkeypatch.setenv("CUSTOM_IMAGE", "test-functions-image")
    return commands


def docker_environment(command):
    # Docker reads all env files in order, then applies inline environment flags.
    args = command["args"]
    return command["file_settings"] + [
        args[index + 1] for index, token in enumerate(args[:-1]) if token == "-e"
    ]


@pytest.mark.parametrize("source", [None, CONFIGURED_SOURCE, ""])
def test_environment_start_forwards_only_configured_bundle_source(
        monkeypatch, docker_commands, source):
    monkeypatch.delenv(BUNDLE_SOURCE, raising=False)
    if source is not None:
        monkeypatch.setenv(BUNDLE_SOURCE, source)
    monkeypatch.setenv("UNRELATED_PIPELINE_SECRET", "must-not-be-forwarded")
    environment = LinuxConsumptionTestEnvironment(use_azurite=False,
                                                 storage_connection_string="test-storage",
                                                 environment_id="bundle-source-test")
    for setup in ("_ensure_blob_container", "_generate_container_sas", "_upload_app_packages"):
        monkeypatch.setattr(environment, setup, lambda: None)

    environment.start()

    settings = docker_environment(docker_commands[0])
    bundle_settings = [item for item in settings if item.startswith(f"{BUNDLE_SOURCE}=")]
    assert bundle_settings == ([] if source is None else [f"{BUNDLE_SOURCE}={source}"])
    assert not any(item.startswith("UNRELATED_PIPELINE_SECRET=") for item in settings)
    assert environment.url == "http://localhost:12345"
    assert len(docker_commands[0]["env_files"]) == (0 if source is None else 1)
    assert all(not path.exists() for path in docker_commands[0]["env_files"])


@pytest.mark.parametrize("source", [OVERRIDE_SOURCE, ""])
def test_explicit_container_environment_overrides_pipeline_source(
        monkeypatch, docker_commands, source):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    controller = FunctionsContainerController(site_name="explicit-container")
    explicit_env = {BUNDLE_SOURCE: source}

    controller.spawn_container(env=explicit_env)

    settings = docker_environment(docker_commands[0])
    assert [item for item in settings if item.startswith(f"{BUNDLE_SOURCE}=")] == [
        f"{BUNDLE_SOURCE}={CONFIGURED_SOURCE}", f"{BUNDLE_SOURCE}={source}",
    ]
    assert explicit_env == {BUNDLE_SOURCE: source}


def test_custom_docker_flags_override_pipeline_source(monkeypatch, docker_commands):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    controller = FunctionsContainerController(
        site_name="custom-flags", docker_flags=["-e", f"{BUNDLE_SOURCE}={OVERRIDE_SOURCE}"])

    controller.spawn_container()

    settings = docker_environment(docker_commands[0])
    assert [item for item in settings if item.startswith(f"{BUNDLE_SOURCE}=")] == [
        f"{BUNDLE_SOURCE}={CONFIGURED_SOURCE}", f"{BUNDLE_SOURCE}={OVERRIDE_SOURCE}",
    ]


def test_full_docker_flag_override_keeps_caller_owned_environment(monkeypatch, docker_commands):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    flags = ["-e", f"{BUNDLE_SOURCE}={OVERRIDE_SOURCE}"]
    controller = FunctionsContainerController(
        site_name="full-override", docker_flags=flags, override_docker_flags=True)

    controller.spawn_container(env={BUNDLE_SOURCE: "ignored-as-before"})

    assert docker_commands[0]["args"][2:-1] == flags
    assert docker_commands[0]["env_files"] == []


@pytest.mark.parametrize("combined_flag", [False, True])
@pytest.mark.parametrize("source", [None, OVERRIDE_SOURCE, ""])
def test_caller_env_file_retains_precedence(
        monkeypatch, docker_commands, tmp_path, combined_flag, source):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    env_file = tmp_path / "caller.env"
    contents = "EXPLICIT_APP_SETTING=preserved\n"
    if source is not None:
        contents += f"{BUNDLE_SOURCE}={source}\n"
    env_file.write_text(contents)
    flags = [f"--env-file={env_file}"] if combined_flag else ["--env-file", str(env_file)]
    controller = FunctionsContainerController(site_name="env-file", docker_flags=flags)

    controller.spawn_container()

    effective = dict(item.split("=", 1) for item in docker_environment(docker_commands[0]))
    assert effective[BUNDLE_SOURCE] == (CONFIGURED_SOURCE if source is None else source)
    assert effective["EXPLICIT_APP_SETTING"] == "preserved"
    assert docker_commands[0]["env_files"][1] == env_file
    assert not docker_commands[0]["env_files"][0].exists()
    assert env_file.read_text() == contents


@pytest.mark.parametrize("source", ["https://example.com\nUNRELATED_PIPELINE_SECRET=value",
                                    "https://example.com\rOTHER=value"])
def test_inherited_source_cannot_inject_more_env_file_entries(monkeypatch, docker_commands, source):
    monkeypatch.setenv(BUNDLE_SOURCE, source)
    controller = FunctionsContainerController(site_name="invalid-source")

    with pytest.raises(ValueError, match="must not contain line breaks"):
        controller.spawn_container()

    assert docker_commands == []


@pytest.mark.parametrize("exception", [False, True])
def test_temporary_defaults_are_removed_on_docker_failure(monkeypatch, docker_commands, exception):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    capture_run = functions_container_controller.subprocess.run

    def fail_run(args, **kwargs):
        capture_run(args, **kwargs)
        if exception:
            raise OSError("docker unavailable")
        return subprocess.CompletedProcess(args, 1, stdout=b"", stderr=b"docker failed")

    monkeypatch.setattr(functions_container_controller.subprocess, "run", fail_run)
    controller = FunctionsContainerController(site_name="failed-start")

    with pytest.raises(OSError if exception else RuntimeError, match="docker"):
        controller.spawn_container()

    assert all(not path.exists() for path in docker_commands[0]["env_files"])


@pytest.mark.parametrize("source", [None, OVERRIDE_SOURCE, ""])
def test_specialization_preserves_explicit_app_source(monkeypatch, docker_commands, source):
    monkeypatch.setenv(BUNDLE_SOURCE, CONFIGURED_SOURCE)
    monkeypatch.setenv("UNRELATED_PIPELINE_SECRET", "must-not-be-forwarded")
    controller = FunctionsContainerController(site_name="specialization")
    controller.spawn_container()
    requests = []

    def send_request(request):
        requests.append(request)
        response = functions_container_controller.requests.Response()
        response.status_code = 200
        return response

    monkeypatch.setattr(controller, "send_request", send_request)
    app_settings = {"SCM_RUN_FROM_PACKAGE": "https://example.com/app.zip"}
    if source is not None:
        app_settings[BUNDLE_SOURCE] = source

    controller.assign_container(env=app_settings)

    payload = json.loads(requests[-1].data)["encryptedContext"]
    iv, ciphertext, _ = (base64.b64decode(part) for part in payload.split("."))
    key = base64.b64decode(controller._container_encryption_key)
    decryptor = Cipher(algorithms.AES(key), modes.CBC(iv)).decryptor()
    plaintext = decryptor.update(ciphertext) + decryptor.finalize()
    unpadder = padding.PKCS7(algorithms.AES.block_size).unpadder()
    context = json.loads(unpadder.update(plaintext) + unpadder.finalize())
    settings = context["Environment"]
    assert requests[-1].url == "http://localhost:12345/admin/instance/assign"
    assert settings["SCM_RUN_FROM_PACKAGE"] == "https://example.com/app.zip"
    assert "UNRELATED_PIPELINE_SECRET" not in settings
    if source is None:
        assert BUNDLE_SOURCE not in settings
    else:
        assert settings[BUNDLE_SOURCE] == source
    assert f"{BUNDLE_SOURCE}={CONFIGURED_SOURCE}" in docker_environment(docker_commands[0])
