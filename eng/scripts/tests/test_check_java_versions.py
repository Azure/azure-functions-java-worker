import importlib.util
import json
import unittest
from pathlib import Path
from unittest.mock import MagicMock, patch
from urllib.error import URLError


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "check-java-versions.py"
SPEC = importlib.util.spec_from_file_location("check_java_versions", SCRIPT_PATH)
checker = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(checker)


class CheckJavaVersionsTests(unittest.TestCase):
    def release_response(self, payload):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = json.dumps(payload).encode()
        return response

    def test_temurin_metadata_uses_publisher_github_release(self):
        with patch.object(checker, "urlopen", return_value=self.release_response(
                {"tag_name": "jdk8u504-b01"})) as urlopen:
            self.assertEqual(checker.get_jdk8_version_from_adoptium(), ("504", "01"))
        request = urlopen.call_args.args[0]
        self.assertEqual(
            request.full_url,
            "https://api.github.com/repos/adoptium/temurin8-binaries/releases/latest",
        )
        self.assertTrue(request.get_header("User-agent"))
        self.assertEqual(urlopen.call_args.kwargs["timeout"], 30)

    def test_temurin_release_preserves_security_and_build_components(self):
        for tag, expected in (
                ("jdk8u472-b08", ("472", "08")),
                ("jdk8u504-b01", ("504", "01")),
                ("jdk8u504-b123", ("504", "123"))):
            with self.subTest(tag=tag), patch.object(
                    checker, "urlopen", return_value=self.release_response({"tag_name": tag})):
                self.assertEqual(checker.get_jdk8_version_from_adoptium(), expected)

    def test_unrecognized_release_metadata_fails_explicitly(self):
        for payload in (
                {}, {"tag_name": None}, {"tag_name": 8}, [],
                {"tag_name": "jdk-11.0.32+7"}, {"tag_name": "jdk8u504-b01-ea"},
                {"tag_name": "prefix-jdk8u504-b01"}):
            with self.subTest(payload=payload), patch.object(
                    checker, "urlopen", return_value=self.release_response(payload)):
                with self.assertRaisesRegex(ValueError, "Temurin 8 release tag"):
                    checker.get_jdk8_version_from_adoptium()

    def test_metadata_transport_error_keeps_existing_error_reporting(self):
        with patch.object(checker, "urlopen", side_effect=URLError("unavailable")), \
                patch("builtins.print") as output:
            self.assertEqual(checker.get_jdk8_version_from_adoptium(), (None, None))
        self.assertTrue(any("Error fetching" in str(call) for call in output.call_args_list))

    def test_metadata_json_error_keeps_existing_error_reporting(self):
        response = MagicMock()
        response.__enter__.return_value.read.return_value = b"invalid JSON"
        with patch.object(checker, "urlopen", return_value=response), \
                patch("builtins.print") as output:
            self.assertEqual(checker.get_jdk8_version_from_adoptium(), (None, None))
        self.assertTrue(any("Error parsing" in str(call) for call in output.call_args_list))

    def test_temurin_downloads_pin_the_detected_release_for_both_platforms(self):
        for platform, extension in (("linux", "tar.gz"), ("windows", "zip")):
            with self.subTest(platform=platform), \
                    patch.object(checker, "get_jdk8_version_from_adoptium",
                                 return_value=("472", "08")), \
                    patch.object(checker, "download_and_extract_jdk",
                                 return_value=Path("jdk")) as download, \
                    patch.object(checker, "validate_jdk_version", return_value=True) as validate:
                self.assertEqual(checker.check_jdk_version(8, platform), ("472", "08"))
                self.assertEqual(
                    download.call_args.args[0],
                    "https://github.com/adoptium/temurin8-binaries/releases/download/"
                    f"jdk8u472-b08/OpenJDK8U-jdk_x64_{platform}_hotspot_8u472b08.{extension}",
                )
                self.assertEqual(download.call_args.args[1], platform)
                validate.assert_called_once_with(Path("jdk"), platform, "1.8.0_472")

    def test_temurin_validation_failure_still_fails_the_pipeline(self):
        with patch.object(checker, "get_jdk8_version_from_adoptium",
                          return_value=("472", "08")), \
                patch.object(checker, "download_and_extract_jdk", return_value=Path("jdk")), \
                patch.object(checker, "validate_jdk_version", return_value=False):
            with self.assertRaises(SystemExit) as error:
                checker.check_jdk_version(8, "windows")
        self.assertEqual(error.exception.code, 1)

    def test_microsoft_release_components_are_unchanged(self):
        for version in ("11.0.32", "21.0.12.1", "11.0.13.8.1"):
            response = MagicMock()
            response.__enter__.return_value.url = (
                "https://download.visualstudio.microsoft.com/download/pr/example/"
                f"microsoft-jdk-{version}-windows-x64.zip"
            )
            with self.subTest(version=version), patch.object(
                    checker, "urlopen", return_value=response):
                self.assertEqual(checker.get_version_from_url("https://aka.ms/download-jdk/example"),
                                 version)


if __name__ == "__main__":
    unittest.main()
