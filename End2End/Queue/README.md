### Prerequisites
- An [Azure Storage Account](https://docs.microsoft.com/en-us/azure/storage/common/storage-quickstart-create-account?tabs=portal)
- [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/) (Insert items to Blob Container)
- [Postman](https://www.getpostman.com/)

### Steps
1. Open `local.settings.json` under `Blob\src\main\functions` folder, set `AzureWebJobsStorage` with your azure storage account connection string.
2. Run `mvn clean package` under `Queue` folder to build the project.
3. Run `func start` under `Queue\src\main\functions` folder to start the function
4. In the `Azure Storage Explorer`, find your storage account. Under `Queues` create a queue named `myqueue` 

### Queue Trigger
- Add a messge in `myqueue` and the function will get triggered.

### Queue Output
- Copy the `queueOut` url from log, then use postman send a POST request to this url, the function will get triggered.