#!/usr/bin/env bash
#
# Bootstraps the Azure Artifacts Maven credential provider for local development.
#
# Maven packages for this repository are restored from an Azure Artifacts feed. Reads are anonymous,
# so this script is only needed by Microsoft developers who have to ingest a package version that
# the feed has not cached yet.
#
# The script:
#   1. Verifies the credential provider is present in the local Maven repository, and downloads it
#      from the public AzureArtifacts tools feed if it is not.
#   2. Writes '.mvn/extensions.xml' at the root of the repository so Maven loads the provider.
#
# '.mvn/' is intentionally listed in .gitignore. The extension exits when it detects a build context,
# and committing it would force an authenticated restore on anonymous consumers. Azure Pipelines
# uses the MavenAuthenticate@0 task instead.
#
# See https://eng.ms/docs/coreai/devdiv/one-engineering-system-1es/1es-docs/azure-artifacts/maven-credprovider

set -euo pipefail

GROUP_ID='com.microsoft.azure'
ARTIFACT_ID='artifacts-maven-credprovider'
BOOTSTRAP_FEED='https://pkgs.dev.azure.com/artifacts-public/PublicTools/_packaging/AzureArtifacts/maven/v1'

# Maven records the extension against this repository id. It must match the <id> of the repositories
# declared in this repository's pom.xml files, otherwise resolution fails validation later.
REPOSITORY_ID='central'

version='3.2.1'
local_repository_path=''
force=false

usage() {
    cat <<'EOF'
Usage: install-maven-credprovider.sh [options]

Options:
  -v, --version <version>            Version of the credential provider to install.
  -l, --local-repository <path>      Path to the local Maven repository. Defaults to ~/.m2/repository.
  -f, --force                        Overwrite an unmanaged .mvn/extensions.xml and re-download the
                                     credential provider even when it is already installed.
  -h, --help                         Show this help text.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -v|--version)
            [[ $# -ge 2 ]] || { echo "error: $1 requires a value" >&2; exit 1; }
            version="$2"
            shift 2
            ;;
        -l|--local-repository)
            [[ $# -ge 2 ]] || { echo "error: $1 requires a value" >&2; exit 1; }
            local_repository_path="$2"
            shift 2
            ;;
        -f|--force)
            force=true
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "error: unknown argument '$1'" >&2
            usage >&2
            exit 1
            ;;
    esac
done

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/../.." && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
    echo "error: Maven ('mvn') was not found on PATH. Install Apache Maven 3.0 or above and try again." >&2
    exit 1
fi

local_repository_specified=true
if [[ -z "$local_repository_path" ]]; then
    local_repository_specified=false
    local_repository_path="$HOME/.m2/repository"
fi

group_path="${GROUP_ID//./\/}"
artifact_path="$local_repository_path/$group_path/$ARTIFACT_ID/$version/$ARTIFACT_ID-$version.jar"

if [[ -f "$artifact_path" && "$force" != true ]]; then
    echo "Credential provider $version is already installed at '$artifact_path'."
else
    echo "Installing credential provider $version from the public tools feed..."

    # The bootstrap must run outside of any Maven project so that this repository's own repository
    # and extension configuration does not take part in resolving the extension itself.
    working_directory="$(mktemp -d)"
    cleanup() { rm -rf "$working_directory"; }
    trap cleanup EXIT

    mvn_args=(
        --batch-mode
        dependency:get
        "-Dartifact=${GROUP_ID}:${ARTIFACT_ID}:${version}"
        "-DremoteRepositories=${REPOSITORY_ID}::::${BOOTSTRAP_FEED}"
    )

    if [[ "$local_repository_specified" == true ]]; then
        mvn_args+=("-Dmaven.repo.local=$local_repository_path")
    fi

    (cd "$working_directory" && mvn "${mvn_args[@]}")

    if [[ ! -f "$artifact_path" ]]; then
        echo "error: bootstrap reported success but '$artifact_path' was not found." >&2
        echo "If a mirror is configured in your settings.xml, temporarily disable it and retry." >&2
        exit 1
    fi

    echo "Installed credential provider to '$artifact_path'."
fi

extensions_directory="$repo_root/.mvn"
extensions_path="$extensions_directory/extensions.xml"

if [[ -f "$extensions_path" && "$force" != true ]]; then
    if ! grep -q "$ARTIFACT_ID" "$extensions_path"; then
        echo "error: '$extensions_path' already exists and declares extensions this script does not manage." >&2
        echo "Review it manually, or re-run with --force to overwrite it." >&2
        exit 1
    fi

    if grep -qE "<version>[[:space:]]*${version//./\\.}[[:space:]]*</version>" "$extensions_path"; then
        echo "'$extensions_path' is already configured for version $version."
        echo 'Done.'
        exit 0
    fi
fi

mkdir -p "$extensions_directory"
cat >"$extensions_path" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!--
  Generated by eng/scripts/install-maven-credprovider.sh. Do not commit this file: it is ignored by
  .gitignore because the extension exits inside build environments and would break anonymous package
  restore for other consumers.
-->
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>$GROUP_ID</groupId>
    <artifactId>$ARTIFACT_ID</artifactId>
    <version>$version</version>
  </extension>
</extensions>
EOF

echo "Wrote '$extensions_path' for version $version."
echo 'Done.'
