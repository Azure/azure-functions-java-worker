# Contributor Onboarding
Thank you for taking the time to contribute to Azure Functions in [Java](https://go.java/)

## Table of Contents

- [Contributor Onboarding](#contributor-onboarding)
  - [Table of Contents](#table-of-contents)
  - [What should I know before I get started](#what-should-i-know-before-i-get-started)
  - [Pre-requisites](#pre-requisites)
  - [Pull Request Change flow](#pull-request-change-flow)
  - [Development Setup](#development-setup)
    - [Visual Studio Code Extensions](#visual-studio-code-extensions)
    - [Setting up the  end-to-end debugging environment](#setting-up-the-end-to-end-debugging-environment)
  - [Pre Commit Tasks](#pre-commit-tasks)
    - [Running unit tests](#running-unit-tests)
  - [Continuous Integration Guidelines & Conventions](#continuous-integration-guidelines--conventions)
  - [Getting help](#getting-help)
    - [Requesting a release](#requesting-a-release)

## What should I know before I get started
- [Azure Functions Java Quickstart](https://docs.microsoft.com/en-us/azure/azure-functions/functions-create-first-azure-function-azure-cli?tabs=bash%2Cbrowser&pivots=programming-language-java)
- [Azure Function Java developer guide](https://docs.microsoft.com/en-us/azure/azure-functions/functions-reference-java?tabs=consumption)

## Pre-requisites

- OS
    - MacOS, Ubuntu (or) Windows10
- Language Runtimes
    -  Java 1.8

- IDE
   - [IntelliJ](https://www.jetbrains.com/idea/download)
   - [Java extension in VS Code](https://code.visualstudio.com/docs/languages/java)
- Java Tools 
    - [Maven](https://maven.apache.org/install.html)
    - [JDK (Azul Zulu for Azure) 8](https://www.azul.com/downloads/azure-only/zulu/?version=java-8-lts&architecture=x86-64-bit&package=jdk)
- Azure Tools
    - [Azure Storage Emulator](https://docs.microsoft.com/en-us/azure/storage/common/storage-use-emulator) (or) [Create a storage account in Azure](https://docs.microsoft.com/en-us/azure/storage/common/storage-account-create?tabs=azure-portal)
    - [Azure Functions Core Tools](https://github.com/Azure/azure-functions-core-tools) v2.7.x and above.
    - [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/)
  

## Pull Request Change flow

The general flow for making a change to the library is:

1. 🍴 Fork the [worker](https://github.com/helayoty/azure-functions-java-worker) repo (add the fork via `git remote add me <clone url here>`
2. 🌳 Create a branch for your change (generally branch from dev) (`git checkout -b my-change`)
3. 🛠 Make your change
4. ✔️ Test your change
5. ⬆️ Push your changes to your fork (`git push me my-change`)
6. 💌 Open a PR to the dev branch
7. 📢 Address feedback and make sure tests pass (yes even if it's an "unrelated" test failure)
8. 📦 [Rebase](https://git-scm.com/docs/git-rebase) your changes into  meaningful commits (`git rebase -i HEAD~N` where `N` is commits you want to squash)
9. :shipit: Rebase and merge (This will be done for you if you don't have contributor access)
10. ✂️ Delete your branch (optional)

## Development Setup

### Visual Studio Code Extensions

The following extensions should be installed if using Visual Studio Code for debugging:

- [Java debugging for Visual Studio Code](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-debug) (Java for VSCode extension)
- Azure Functions Extensions for Visual Studio Code v0.19.1 and above.


### Setting up the  end-to-end debugging environment

1. Install [Visual Studio 2019](https://visualstudio.microsoft.com/downloads/) 

2. Git clone the [Azure function host](https://github.com/Azure/azure-functions-host) code base open it using Visual Studio 2019
 
3. Use any starter sample from this [folder] in your fork (https://github.com/Azure/azure-functions-host/tree/dev/sample/Java) and run *`mvn clean package`*

4. Add the following environment variables into your debugging configuration:
    
    | Variable   |  Value    |
    | :--------: | :------:  |
    | FUNCTIONS_WORKER_RUNTIME       | java |
    | languageWorkers:java:arguments | -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 |
    | AZURE_FUNCTIONS_ENVIRONMENT    | Development |
    | AzureWebJobsScriptRoot         | a path to your azure function target folder,i.e. ~/< your-folder-path >/target/azure-functions/<azure-function-name-####> |
    
    >> Note: In macOS, you might need to add JAVA_HOME as a debugging environment variable. 

5. Run the host in a debugging mood
   
   >> Note: Make sure that WebJobs.Script.WebHost is set as a Startup Project

6. Git clone your fork for [azure function worker](https://github.com/helayoty/azure-functions-java-worker) and open it in the IDE.

7. Add new remote debugging configuration as show below

![](tools/.images/worker-debug-configuration.png)

8. Run the worker in the debugging mode 
    
7. Set breakpoints and click Run -> Start Debugging in VS Code. This should internally start the Azure Function using `func host start` command.

## Pre Commit Tasks

>>TODO

### Running unit tests

1. Add your unit tests under ./src/test folder
2. Run: `mvn clean package`

## Continuous Integration Guidelines & Conventions

This project uses a combination of Azure DevOps and GitHub Actions for CI/CD.

- For each PR request/merge, a continuous integration pipeline will run internally that performs linting and running unit tests on your PR/merge.
- A GitHub Action will also perform CI tasks against your PR/merge. This is designed to provide more control to the contributor.

## Getting help

 - Leave comments on your PR and @username for attention

### Requesting a release
- If you need a release into maven central, request it by raising an issue and tagging @TsuyoshiUshio and @amamounelsayed


