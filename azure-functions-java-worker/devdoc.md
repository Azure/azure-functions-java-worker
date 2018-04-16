# Contributing

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

# Environment Setup

## Maven

* Run all maven commands under the root folder of this repository

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
