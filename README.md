# Contributing

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/). For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

# Environment Setup

## Maven

* Run all maven commands under this repository folder

## IntelliJ

* Import this repository folder as an existing project in IntelliJ
* Configure the Language level (under Project Structure -> Modules -> Sources) to 8

## Eclipse

* Set workspace to the parent folder of this repository
* Import this repository folder as an existing Maven project in Eclipse
* Configure the project Java compiler compliance level to 1.8
* Set the JRE libraries to JRE 1.8
* "Ignore optional compiler problems" in "Java Build Path" for "target/generated-sources/\*\*/\*.java"

# Coding Convention

## Version Management

Out version strategy just follows the maven package version convention: `<major>.<minor>.<hotfix>-<prerelease>`, where:

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
