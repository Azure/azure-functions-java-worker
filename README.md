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
