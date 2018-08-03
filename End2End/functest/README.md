# Microsoft Azure Functions in Java

This repo contains several Java Azure Functions samples for different events.

## Prerequisites
- JDK 1.8
- Maven 3.0+
- [.NET Core SDK](https://www.microsoft.com/net/learn/get-started/windows)
- [Azure Functions Core Tools v2](https://docs.microsoft.com/en-us/azure/azure-functions/functions-run-local#v2)
- An active azure subscription
- [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/)
- [Postman](https://www.getpostman.com/)

## Sample List and Test Status
| Event | Binding | Test Status |
| ---------- | ----------- | -------- |
| HTTP  | HttpTrigger | `pass` |
| Timer | Timer | TimerTrigger | `pass` |
| Storage Blob | BlobTrigger <br> BlobInput <br> BlobOutput | `-` <br> `-` <br> `-` |
| Storage Queue | QueueTrigger <br> QueueOutput | `pass` <br> `pass` |
| Storage Table | TableInput <br> TableOutput| `-` <br> `-`|
| Cosmos DB | CosmosDBTrigger <br> CosmosDBInput <br> CosmosDBOutput| `-` <br> `-` <br> `-`|
| Service Bus Queue | ServiceBusQueueTrigger <br> ServiceBusQueueOutput | `-` <br> `-` |
| Service Bus Topic | ServiceBusTopicTrigger <br> ServiceBusTopicOutput | `-` <br> `-` |
| Event Hubs | EventHubsTrigger <br> EventHubsOutput | `-` <br> `-` |
| Event Grid | EventGridTrigger | `-` |


## Usage
### Prerequisites
- An [Azure Storage Account](https://docs.microsoft.com/en-us/azure/storage/common/storage-quickstart-create-account?tabs=portal)
- [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/)
- [Postman](https://www.getpostman.com/)

### Steps
1. Open `local.settings.json` under `functest\src\main\functions` folder, set `AzureWebJobsStorage` with your azure storage account connection string. 
2. Run `mvn clean package` under `Queue` folder to build the project.
3. Run `func start` under `functest\src\main\functions` folder to start the functions

#### Http Trigger
1. Use whatever tools you want to send an http request to the function. For example: 
open a browser, copy the `httpTrigger` url from log to browser and add a request string `name=world`, press enter. The url should be like: `
http://localhost:7071/api/HttpTriggerJava?name=world`

#### Timer Trigger
1. Since the schedule we are using in the example is `0 */1 * * * *`, you can observe that the function will be triggered every 1 minute.

#### Queue Trigger
1. Open `Azure Storage Explorer`, find your storage account. Create a queue named `myqueue` under `Queues` if it doesn't exist. 
2. Add a messge in `myqueue` and the function will get triggered.

#### Queue Output
1. Open `Azure Storage Explorer`, find your storage account. Create a queue named `myqueue` under `Queues` if it doesn't exist. 
2. Copy the `queueOutput` url from log, then use postman send a POST request to this url, the function will get triggered.