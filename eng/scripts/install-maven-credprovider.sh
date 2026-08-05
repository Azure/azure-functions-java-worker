#!/usr/bin/env bash

set -euo pipefail

group_id='com.microsoft.azure'
artifact_id='artifacts-maven-credprovider'
bootstrap_feed='https://pkgs.dev.azure.com/artifacts-public/PublicTools/_packaging/AzureArtifacts/maven/v1'
repository_id='central'
version='3.2.1'
local_repository_path=''
force=false

usage() {
  cat <<'EOF'
Usage: install-maven-credprovider.sh [options]

Options:
  -v, --version <version>        Credential provider version to install.
  -l, --local-repository <path>  Maven local repository path.
  -f, --force                    Reinstall and overwrite .mvn/extensions.xml.
  -h, --help                     Show this help text.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -v|--version) [[ $# -ge 2 ]] || { echo "error: $1 requires a value" >&2; exit 1; }; version="$2"; shift 2 ;;
    -l|--local-repository) [[ $# -ge 2 ]] || { echo "error: $1 requires a value" >&2; exit 1; }; local_repository_path="$2"; shift 2 ;;
    -f|--force) force=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "error: unknown argument '$1'" >&2; usage >&2; exit 1 ;;
  esac
done

command -v mvn >/dev/null 2>&1 || { echo "error: Maven ('mvn') was not found on PATH." >&2; exit 1; }

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"
custom_local_repository=true
if [[ -z "$local_repository_path" ]]; then
  custom_local_repository=false
  local_repository_path="$HOME/.m2/repository"
fi

group_path="${group_id//./\/}"
artifact_path="$local_repository_path/$group_path/$artifact_id/$version/$artifact_id-$version.jar"
if [[ "$force" == true || ! -f "$artifact_path" ]]; then
  working_directory="$(mktemp -d)"
  trap 'rm -rf "$working_directory"' EXIT
  arguments=(
    --batch-mode
    dependency:get
    "-Dartifact=${group_id}:${artifact_id}:${version}"
    "-DremoteRepositories=${repository_id}::::${bootstrap_feed}"
  )
  if [[ "$custom_local_repository" == true ]]; then
    arguments+=("-Dmaven.repo.local=$local_repository_path")
  fi
  (cd "$working_directory" && mvn "${arguments[@]}")
fi

[[ -f "$artifact_path" ]] || { echo "error: credential provider was not found at '$artifact_path'." >&2; exit 1; }

extensions_directory="$repo_root/.mvn"
extensions_path="$extensions_directory/extensions.xml"
if [[ -f "$extensions_path" && "$force" != true ]]; then
  grep -q "$artifact_id" "$extensions_path" || { echo "error: '$extensions_path' contains an unmanaged Maven extension." >&2; exit 1; }
  if grep -qE "<version>[[:space:]]*${version//./\.}[[:space:]]*</version>" "$extensions_path"; then
    echo "Maven credential provider $version is already configured."
    exit 0
  fi
fi

mkdir -p "$extensions_directory"
cat >"$extensions_path" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>$group_id</groupId>
    <artifactId>$artifact_id</artifactId>
    <version>$version</version>
  </extension>
</extensions>
EOF

echo "Configured Maven credential provider $version in '$extensions_path'."