![Azure Functions Logo](https://raw.githubusercontent.com/Azure/azure-functions-cli/refs/heads/main/eng/res/functions.png)

|Branch|Status|
|---|---|
|Dev|[![Build status](https://dev.azure.com/azfunc/Azure%20Functions/_apis/build/status/Azure.azure-functions-java-worker?branchName=dev)](https://dev.azure.com/azfunc/Azure%20Functions/_build/latest?definitionId=20&branchName=dev)|
|v3|[![Build status](https://dev.azure.com/azfunc/Azure%20Functions/_apis/build/status/Azure.azure-functions-java-worker?branchName=v3.x)](https://dev.azure.com/azfunc/Azure%20Functions/_build/latest?definitionId=20&branchName=v3.x)|

# Contributing

Please refer to [CONTRIBUTING.md](./CONTRIBUTING.md) for more information.

# Environment Setup

## Maven

* Run all maven commands under the root folder of this repository

### Package feed

All Maven packages and plugins are restored from the `upstream-public` Azure Artifacts feed
(`https://pkgs.dev.azure.com/azfunc/public/_packaging/upstream-public/maven/v1`), which is configured
as the `central` repository in every `pom.xml` in this repository.

The repository root also has a [`settings.xml`](settings.xml) that mirrors `central` to the same
feed. It exists because a `pom.xml` cannot cover everything:

- Maven resolves build extensions and plugin prefixes *before* a pom's `<repositories>` are honored,
	so those requests would otherwise go straight to Maven Central.
- `MavenAuthenticate@0` and the credential provider key credentials off the Azure Artifacts *feed
	name* (`upstream-public`), while the pom repository id must be `central` in order to override the
	id Maven inherits from the Super POM. The mirror id bridges the two.

CI installs this file to `~/.m2/settings.xml`. Locally you only need it when pulling a package or
version the feed has not cached yet, in which case pass it explicitly with `mvn -s settings.xml`.

#### Anonymous restore (default)

The feed allows anonymous reads, so no credentials are required to build once a package version has
been saved to the feed. External contributors and fresh clones need no setup. `mvn` just works.
Never commit credentials or a `<server>` entry to `settings.xml` in this repository because doing so
would force authentication on everyone.

#### Authenticating (Microsoft developers only)

Authentication is only needed to *ingest* a package version that the feed has not cached yet. The
first restore of any new or upgraded dependency will fail anonymously with:

> No local versions of package '...'; please provide authentication to access versions from upstream
> that have not yet been saved to your feed.

When that happens, a Microsoft developer with access to the `azfunc/public` project must run the
restore once with credentials, which pulls the version from upstream and saves it to the feed. Every
subsequent anonymous restore then succeeds.

The recommended way to authenticate is the `artifacts-maven-credprovider`, which acquires a token via
Entra ID so you do not have to manage a PAT.

Run the helper script for your shell from the root of your clone. It installs the credential provider
into your local Maven repository if it is missing, then writes `.mvn/extensions.xml`. Both scripts
are idempotent, so re-running them is safe:

```powershell
./eng/scripts/Install-MavenCredentialProvider.ps1
```

```bash
./eng/scripts/install-maven-credprovider.sh
```

Pass `-Version` / `--version` to install a different release, and `-Force` / `--force` to reinstall or
to overwrite an `.mvn/extensions.xml` the script does not manage.

If you would rather do it by hand, the equivalent steps are:

1. Bootstrap the credential provider once per machine. Run this from a directory outside any Maven
	 project, such as your home directory. It downloads the extension from the public `AzureArtifacts`
	 tools feed, which needs no authentication:

	 ```powershell
	 mvn dependency:get "-Dartifact=com.microsoft.azure:artifacts-maven-credprovider:3.2.1" "-DremoteRepositories=central::::https://pkgs.dev.azure.com/artifacts-public/PublicTools/_packaging/AzureArtifacts/maven/v1"
	 ```

	 Using the repository id `central` matters. Maven records the extension as having come from
	 `central`, which is the same id this repository's `pom.xml` files declare, so the cached copy
	 validates during later builds.

2. Create `.mvn/extensions.xml` at the root of your clone:

	 ```xml
	 <extensions xmlns="http://maven.apache.org/EXTENSIONS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
		 xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.1.0 https://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
		 <extension>
			 <groupId>com.microsoft.azure</groupId>
			 <artifactId>artifacts-maven-credprovider</artifactId>
			 <version>3.2.1</version>
		 </extension>
	 </extensions>
	 ```

`.mvn/` is deliberately listed in `.gitignore`. Do not commit it. The extension exits when it
detects a build context, and committing it would break anonymous restores for everyone else.

If you would rather not use the credential provider, you can instead add a `<server>` entry to your
user-level `~/.m2/settings.xml` (never to a file inside this repository), using an Azure DevOps
personal access token with Packaging read and write scope:

```xml
<settings>
	<servers>
		<server>
			<!-- Must match the <id> of the repository declared in the pom.xml files. -->
			<id>central</id>
			<username>azfunc</username>
			<password>[PERSONAL_ACCESS_TOKEN]</password>
		</server>
	</servers>
</settings>
```

CI covers this automatically. The `MavenAuthenticate@0` task in the build templates authenticates the
`central` repository, so merged changes to dependency versions are ingested by the pipeline. The
credential provider is not used in pipelines.

## IntelliJ

* Import the root folder of this repository as an existing project in IntelliJ
* Configure the Language level (under Project Structure -> Modules -> Sources) to 8

## Eclipse

* Set workspace to the parent folder of this repository
* Import the root folder of this repository as an existing Maven project in Eclipse
* Configure the project Java compiler compliance level to 1.8
* Set the JRE libraries to JRE 1.8
* "Ignore optional compiler problems" in "Java Build Path" for "target/generated-sources/\*\*/\*.java"

# Development Cycle

## Build

This is a maven based project, thus you can use any command line tools or IDEs which support maven to build it. Here we will use command line as the example (you could configure your own development environment accordingly).

To build the project, you just need to run one command from the root folder of this project:

```sh
mvn clean package
```

And the binary will be built to `"./azure-functions-java-worker/target/azure-functions-java-worker-<version>.jar"`.

If you have updated the core interface (azure-functions-java-core), a `mvn clean install` is required for your test functions app to reference the latest core package.

## Host configuration delivery

When an init request contains the exact capability `HostConfiguration = "1"`, the worker
captures `host.json` beneath its own `FUNCTIONS_APPLICATION_DIRECTORY` environment variable.
The launcher must provide this variable before the first requested init; neither
`function_app_directory` from the Host nor the worker installation directory supplies it.
There is no fallback to the current directory. Explicit relative roots retain native filesystem
resolution. This repository's worker launcher configuration does not set the variable.

The worker client captures once, lazily and thread-safely, and replays the immutable result
across subsequent requested inits, including failed captures. Unrequested inits do not access
the root or file and omit the envelope. Restart the worker to capture changed application content.
Missing files at open produce `Missing`; unavailable/invalid roots, read/close failures, invalid
encodings and malformed JSON produce `Error`. A not-found failure after opening is a read error,
not `Missing`. These outcomes do not replace the existing initialization result.

The payload is strict UTF-8 without its source BOM, with UTF-8, UTF-16 LE/BE and UTF-32 LE/BE
BOM detection and strict decoding (valid U+FFFD is preserved, not treated as a decoder error).
Conversion uses bounded incremental I/O. Both the converted source, before JSON normalization,
and the normalized payload are limited to 65,536 UTF-8 bytes, inclusive; exceeding either limit
produces content-free `TooLarge`, even if removing comments would shrink the source below the cap.
The worker normalizes to strict compact JSON: `//` and `/* */` comments and trailing commas are
accepted and removed, as is insignificant whitespace. It accepts a single root of any JSON type
with at most 64 nested containers, retaining duplicate keys, key order, raw string/property
escapes (including escaped lone surrogates), and number spellings without numeric conversion.
Empty decoded content (including BOM-only files) stays `Available` with empty content; non-empty
whitespace/comment-only content and invalid syntax are `Error`. The Host still owns semantic
validation, defaults and app-setting overrides; the worker does not merge environment settings.

An `Error` or `TooLarge` capture logs one warning with a fixed reason code and restart guidance.
It never logs file content, paths or exception details, and cache replay does not repeat the warning.
`Available`, `Missing`, and unrequested delivery do not produce feature diagnostics. These semantics
follow [Azure/azure-functions-dotnet-worker#3522](https://github.com/Azure/azure-functions-dotnet-worker/pull/3522)
at `4c440d8d38526a16eb549ac6fba1082fe5ad4450`.

The vendored `FunctionRpc.proto` contains only the APP-01 additions from
[Azure/azure-functions-language-worker-protobuf#103](https://github.com/Azure/azure-functions-language-worker-protobuf/pull/103),
pinned at `313666583af4e97f0b534f1cbf61a3812f277c12`: `WorkerInitResponse.host_configuration`
(field 6) and `HostConfiguration`. Existing Java options and unrelated protocol definitions
are retained. The usual Maven protobuf plugin generates Java bindings; no generated source
is checked in and no protocol package upgrade is required. The reference protocol PR must be
coordinated before release.

Run the focused delivery and existing startup/transport regressions from the repository root:

```powershell
mvn -B "-Dtest=HostConfigurationSnapshotProviderTest,WorkerInitRequestHandlerTest,HostConfigurationDeliveryTest,ApplicationTest,JavaWorkerClientTest,FunctionEnvironmentReloadRequestHandlerTest,GrpcTransportTest,SimpleParamReturnTest" test
mvn -B package
```

For Host/WorkerProxy integration, use a Host containing
[Azure/azure-functions-host#12101](https://github.com/Azure/azure-functions-host/pull/12101)
(merge commit `8da994a4e19324233a6f0efecea963a5cc5c5c03`) and a proxy using the same schema.
Install `target\azure-functions-java-worker-2.19.5.jar` as the launcher's
`azure-functions-java-worker.jar`, set `FUNCTIONS_APPLICATION_DIRECTORY` in the worker's
environment to the local application content root, and have the Host request
`HostConfiguration = "1"`. For a direct proxy launch, retain the existing transport options:

```powershell
$env:FUNCTIONS_APPLICATION_DIRECTORY = "C:\apps\my-function"
java -jar .\target\azure-functions-java-worker-2.19.5.jar --functions-uri http://localhost:5000 --functions-worker-id java-worker --functions-request-id init --functions-grpc-max-message-length 134217728
```

The proxy must already be listening and forward the new envelope opaquely. HTTPS endpoints
continue to require a trusted server certificate; legacy host/port arguments remain supported.
Host semantic acceptance, defaults and app-setting precedence remain integration concerns.

## Pipeline network isolation

The weekly JDK version check and official build use `DefaultDeny` network isolation while retaining
the `CFSClean`, `CFSClean2`, and `CFSClean3` policies. Temurin 8 metadata and platform archives come
from Adoptium's GitHub releases; each archive is pinned to the detected GA release. Microsoft
OpenJDK version discovery is unchanged.

The official build sets `FUNCTIONS_EXTENSIONBUNDLE_SOURCE_URI` to
`https://cdn-staging.functions.azure.com/public`, the canonical staging CDN hostname, instead of the
legacy hostname configured in the pipeline UI. This keeps staging bundle coverage under network
isolation. Changes to the official YAML reach the internal repository through the code-mirror
pipeline after merging to `dev`.

Both JDK validation jobs run the version-detection regression tests. To run them locally:

```sh
python -m unittest discover -s eng/scripts/tests -p test_check_java_versions.py
```

YAML validation alone does not establish runtime compliance. Check the applied policies in
**Start Network Isolation** and the blocked connections and policy violations in
**Stop Network Isolation** on subsequent pipeline runs.

## Linux CI package setup

The shared emulated-test and Docker-test jobs wait up to 300 seconds for dpkg locks during
`apt-get install`, allowing the agent's unattended upgrades to finish. Installation still fails if
the lock remains held after that wait or another error occurs. `apt-get update` is unchanged: its
separate package-list lock is not covered by `DPkg::Lock::Timeout`.

## Updating Dependencies and Plugins
* Update dependencies
```
mvn versions:use-latest-versions
```
* Update plugins
```
mvn versions:display-plugin-updates

```
For each of the plugin that displayed, update pom.xml

* Update version

```
mvn release:update-versions
```
## Debug

The Java worker alone is not enough to establish the functions app, we also need the support from [Azure Functions Host](https://github.com/Azure/azure-functions-host). You may either use a published host CLI or use the in-development host. But both of the methods require you to attach to the java process if you want a step-by-step debugging experience.

### Published Host

You can install the latest Azure functions CLI tool by:

```sh
npm install -g azure-functions-core-tools@core
```

By default, the binaries are located in `"<Home Folder>/.azurefunctions/bin"`. Copy the `"<Azure Functions Java Worker Root>/azure-functions-java-worker/target/azure-functions-java-worker-<version>.jar"` to `"<Home Folder>/.azurefunctions/bin/workers/java/azure-functions-java-worker.jar"`. And start it normally using:

```sh
func start
```

### Latest Host

A developer may also use the latest host code by cloning the git repository [Azure Functions Host](https://github.com/Azure/azure-functions-host). Now you need to navigate to the root folder of the host project and build it through:

```sh
dotnet restore WebJobs.Script.sln
dotnet build WebJobs.Script.sln
```

After the build succeeded, set the environment variable `"AzureWebJobsScriptRoot"` to the root folder path (the folder which contains the `host.json`) of your test functions app; and copy the `"<Azure Functions Java Worker Root>/azure-functions-java-worker/target/azure-functions-java-worker-<version>.jar"` to `"<Azure Functions Host Root>/src/WebJobs.Script.WebHost/bin/Debug/netcoreapp2.0/workers/java/azure-functions-java-worker.jar"`. Now it's time to start the host:

```sh
dotnet ./src/WebJobs.Script.WebHost/bin/Debug/netcoreapp2.0/Microsoft.Azure.WebJobs.Script.WebHost.dll
```

> Note: Remember to remove `"AzureWebJobsScriptRoot"` environment variable after you have finished debugging, because it will also influence the `func` CLI tool.

## Generate JavaDoc

Simply using the following command to do so (if there are dependency errors, run `mvn clean install` beforehand):

```sh
mvn javadoc:javadoc
```

# Development Notes

Java worker now shades all its jars, to introduce any new jars it is required by the developers to add a section in the pom file to relocate it.

# Coding Convention

## Version Management

Our version strategy just follows the maven package version convention: `<major>.<minor>.<hotfix>-<prerelease>`, where:

* `<major>`: Increasing when incompatible breaking changes happened
* `<minor>`: Increasing when new features added
* `<hotfix>`: Increasing when a hotfix is pushed
* `<prerelease>`: A string representing a pre-release version

**Use `SNAPSHOT` pre-release tag for packages under development**. Here is the sample workflow:

1. Initially the package version is `1.0-SNAPSHOT`. *There is no hotfix for SNAPSHOT*
2. Modify the version to `1.0.0-ALPHA` for internal testing purpose. *Notice the hotfix exists here*
3. After several BUG fixes, update the version to `1.0.0`.
4. Create a new development version `1.1-SNAPSHOT`.
5. Make a new hotfix into `1.0-SNAPSHOT`, and release to version `1.0.1`.
6. New features are added to `1.1-SNAPSHOT`.

Every time you release a non-development version (like `1.0.0-ALPHA` or `1.0.1`), you also need to update the tag in your git repository.

# Advanced Java Concepts

## Reflection for Type

Primitives have two different type definitions, for example: `int.class` (which is identical to `Integer.TYPE`) is not `Integer.class`.

All Java types are represented by `Type` interface, which may be one of the following implementations:
* `Class<?>`: normal class type like `String`
* `ParameterizedType`: generic class type like `List<Integer>`
* `WildcardType`: generic argument contains question mark like `? extends Number`
* `TypeVariable<?>`: generic argument like `T`
* `GenericArrayType`: generic array like `T[]`

For the generic type behaviors (including compile-time validation and runtime type erasure) in Java, please refer to *[Generics in the Java Programming Language
](https://www.cs.rice.edu/~cork/312/Readings/GenericsTutorial.pdf)*.
