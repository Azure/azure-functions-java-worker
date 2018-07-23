### Prerequisites
- An [Azure Storage Account](https://docs.microsoft.com/en-us/azure/storage/common/storage-quickstart-create-account?tabs=portal)

### Steps
1. Open `local.settings.json` under `Timer\src\main\functions` folder, set `AzureWebJobsStorage` with your azure storage account connection string.
2. Run `mvn clean package` under `Timer` folder to build the project.
3. Run `func start` under `Timer\src\main\functions` folder to start the function.
4. Since the schedule we are using in the example is `0 */1 * * * *`, you can observe that the function will be triggered every 1 minute.
