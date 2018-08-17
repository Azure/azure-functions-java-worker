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
|Timer | TimerTrigger | `pass` |
| Storage Blob | BlobTrigger <br> BlobInput <br> BlobOutput | `pass` <br> `pass` <br> `pass` |
| Storage Queue | QueueTrigger <br> QueueOutput | `pass` <br> `pass` |
| Storage Table | TableInput <br> TableOutput| `pass` <br> `pass`|
| Cosmos DB | CosmosDBTrigger <br> CosmosDBInput <br> CosmosDBOutput| `pass` <br> `pass` <br> `pass`|
| Service Bus Queue | ServiceBusQueueTrigger <br> ServiceBusQueueOutput | `pass` <br> `pass` |
| Service Bus Topic | ServiceBusTopicTrigger <br> ServiceBusTopicOutput | `pass` <br> `pass` |
| Event Hubs | EventHubsTrigger <br> EventHubsOutput | `pass` <br> `pass` |
| Event Grid | EventGridTrigger | `pass` |

## Usage
### Prerequisites
- An [Azure Storage Account](https://docs.microsoft.com/en-us/azure/storage/common/storage-quickstart-create-account?tabs=portal)
- An [Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-create-namespace-portal)
- An [Event Hubs](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-create). Under your Event Hubs, create an Event Hub named `myhub`. We will use it in the function example.
- An Azure Cosmos DB. Under your Cosmos DB, create a database `db`, in the database `db`, create a collection `col`. We will use it in the function example.

### Steps
1. Open `local.settings.json` under `functest\src\main\functions` folder. 
    - Set `AzureWebJobsStorage` with your Azure Storage Account connection string. 
    - Set `AzureServiceBus` with your Service Bus connection string. 
    - Set `AzureEventHub` with your Event Hubs connection string. 
    - Set `AzureCosmosDB` with your Azure Cosmos DB connection string.
2. Run `mvn clean package` under `functest` folder to build the project.
3. Run `func start` under `functest\target\azure-functions\azure-functions-EndToEndTests` folder to start the functions

#### Http Trigger
1. Use whatever tools you want to send an http request to the function. For example: 
open a browser, copy the `httpTrigger` url from log to browser and add a request string `name=world`, press enter. The url should be like: `
http://localhost:7071/api/HttpTrigger?name=world`

#### Timer Trigger
1. Since the schedule we are using in the example is `0 */1 * * * *`, you can observe that the function will be triggered every 1 minute.

#### Queue Trigger
1. Open Azure Portal in web browser, find your Azure Storage Account. Create a queue named `myqueue` if it doesn't exist. 
2. Add a messge in `myqueue` and the function will get triggered.

#### Queue Output
1. Open Azure Portal in web browser, find your Azure Storage Account. Create a queue named `myqueue` if it doesn't exist.
2. Copy the `queueOutput` url from log, then use postman send a POST request to this url, the function will get triggered.

#### Blob Trigger
1. Open Azure Portal in web browser, find your Azure Storage Account. Create a blob container named `myblob` if it doesn't exist.
2. Upload a file to `myblob` and the function will get triggered.

#### Blob Input and Output
1. Open Azure Portal in web browser, find your Azure Storage Account. Create a blob container named `myblob` if it doesn't exist. Create a queue named `myblobqueue` if it doesn't exist.
2. Maker sure there is a file, i.e. `blobtest.txt`, in `myblob`, add a message `blobtest.txt` to `myblobqueue`, than the function `blobHandler` will get triggered. And you can find a file named `blobtest.txt-Copy` under `myblob`.

#### Table Input and Output
1. Open `Azure Storage Explorer`, find your Azure Storage Account. Under `Tables` create a table named `Person`. Under `Queues` create a queue named `mytablequeue`.
2. In table `Person`, add an entity with `PartitionKey=firstPartition, RowKey=firstRow, Name=firstName`.
3. In queue `mytablequeue`, add a message `firstRow`, then the Table Input function will get triggered.
1. Copy the `tableOutput` url from log, then use postman send a POST request with a name, let's say `second`, in body to this url, the Table Output function will get triggered.

#### Cosmos DB Trigger
1. Open `Azure Storage Explorer`, find your Auzre Cosmos DB. Create a database `db` if it doesn't exist, in the database `db`, create a collection `col` if it doesn't exist. 
2. Under `col`, add a new document into `documents`, and then the Cosmos DB Trigger function will get triggered.

#### Cosmos DB Input
1. Open `Azure Storage Explorer`, find your Auzre Cosmos DB. Create a database `db` if it doesn't exist, in the database `db`, create a collection `col` if it doesn't exist. 
2. Find your Azure Storage Account. Under `Queues` create a Queue named `mydbqueue` if it doesn't exist.
3. Add a message which equals to the id of one of the documents in Queue `mydbqueue` and the Cosmos DB Input function will get triggered.

#### Cosmos DB Output
1. Open `Azure Storage Explorer`, find your Auzre Cosmos DB. Create a database `db` if it doesn't exist, in the database `db`, create a collection `col` if it doesn't exist. 
2. Copy the `cosmosDBOutOutput` url from log, then use postman send a POST request with a message in the body to this url, the Cosmos DB Output function will get triggered.

#### Service Bus Queue Trigger and Output
1. Open Azure Portal in web browser, find your Service Bus Namespace. Create a Queue named `mysbqueue` if it doesn't exist.
2. Copy the `ServiceBusQueueOutput` url from log, then use postman send a POST request with a message in the body to this url, the Event Hub Output function will get triggered.
3. When Service Bus Queue Output function get triggered and add a message into `mysbqueue`, Service Bus Queue Trigger function will get triggered.

#### Service Bus Topic Trigger and Output
1. Open Azure Portal in web browser, find your Service Bus Namespace. Create a Topic named `mysbtopic` if it doesn't exist. Create a subscription named `mysubs` under `mysbtopic`.
2. Copy the `serviceBusTopicOutput` url from log, then use postman send a POST request with a message in the body to this url, the Event Hub Output function will get triggered.
3. When Service Bus Topic Output function get triggered and add a message into `mysbtopic`, Service Bus Topic Trigger function will get triggered.

#### Event Hub Trigger and Output
1. Open Azure Portal in web browser, find your Event Hubs Namespace. Create an Event Hub named `myhub` if it doesn't exist.
2. Copy the `eventHubOutput` url from log, then use postman send a POST request with a message in the body to this url, the Event Hub Output function will get triggered.
3. When Event Hub Output function get triggered and add a message into `myhub`, Event Hub Trigger function will get triggered.

#### Event Grid Trigger
1. To simply trigger this function, you can just use postman to [manually post a request](https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-event-grid#manually-post-the-request):
    + Post to the URL of your Event Grid trigger function, using the following pattern:
`http://localhost:7071//runtime/webhooks/EventGridExtensionConfig?functionName={functionname}`
    + Set a `Content-Type: application/json` header
    + Set an `aeg-event-type: Notification` header.
    + Paste below data into the request body
    ```
    [{
        "topic": "/subscriptions/{subscriptionid}/resourceGroups/eg0122/providers/Microsoft.Storage/storageAccounts/egblobstore",
        "subject": "/blobServices/default/containers/{containername}/blobs/blobname.jpg",
        "eventType": "Microsoft.Storage.BlobCreated",
        "eventTime": "2018-01-23T17:02:19.6069787Z",
        "id": "{guid}",
        "data": {
        "api": "PutBlockList",
        "clientRequestId": "{guid}",
        "requestId": "{guid}",
        "eTag": "0x8D562831044DDD0",
        "contentType": "application/octet-stream",
        "contentLength": 2248,
        "blobType": "BlockBlob",
        "url": "https://egblobstore.blob.core.windows.net/{containername}/blobname.jpg",
        "sequencer": "000000000000272D000000000003D60F",
        "storageDiagnostics": {
            "batchId": "{guid}"
        }
        },
        "dataVersion": "",
        "metadataVersion": "1"
    }]
    ```

### Troubleshooting
If you get below error message when you run these samples. It's a known issue:[#430](https://github.com/Azure/azure-webjobs-sdk-extensions/issues/430).
```
[8/17/2018 9:14:15 AM] CosmosDBTrigger: The listener for function 'Functions.CosmosDBTrigger' was unable to start.
System.Private.CoreLib: One or more errors occurred. (Unable to load DLL 'Microsoft.Azure.Documents.ServiceInterop.dll'
or one of its dependencies: The specified module could not be found. (Exception from HRESULT: 0x8007007E)).
Microsoft.Azure.DocumentDB.Core: Unable to load DLL 'Microsoft.Azure.Documents.ServiceInterop.dll' 
or one of its dependencies: The specified module could not be found. (Exception from HRESULT: 0x8007007E).
```
To fix this, you can run following command under `functest` folder to get a later version of `Microsoft.Azure.DocumentDB.Core`:
```
func extensions install -p Microsoft.Azure.DocumentDB.Core -v 2.0.0-preview
```
And then run command `mvn clean package` again.