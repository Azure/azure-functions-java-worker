# Build and publishing

In order for the Java Worker to get loaded into the runtime, today we use a NuGet to package the jars for deployment.

## AppVeyor

We're using App Veyor for CI. It builds the Java Core and Worker modules and then packages them into a NuGet for consumption by the Azure Functions Script Host.

CI runs on every PR and nightly on `dev` and `master`

## Local

To produce the build results locally, be sure you have Java, Maven, and NuGet installed.

```powershell
mvn clean install -DskipTests
mkdir pkg
Get-ChildItem -Path .\azure-functions-java-worker\target\* -Include 'azure*' -Exclude '*shaded.jar' | %{ Copy-Item $_.FullName .\pkg\azure-functions-java-worker.jar }
nuget pack ./tools/AzureFunctionsJavaWorker.nuspec -Properties versionsuffix=LOCAL
```

This result in a `.nupkg` file which you can use for adhoc testing.