# Azure Functions in Java

This repo contains several Java Azure Functions samples for different events.

## Prerequisites
- JDK 1.8
- Maven 3.0+
- [.NET Core SDK](https://www.microsoft.com/net/learn/get-started/windows)
- An active azure subscription
- [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/)

## Usage
### Prerequisites
- An [Azure Storage Account](https://docs.microsoft.com/en-us/azure/storage/common/storage-quickstart-create-account?tabs=portal)
- An [Azure Service Bus](https://docs.microsoft.com/en-us/azure/service-bus-messaging/service-bus-create-namespace-portal)
- An [Event Hubs](https://docs.microsoft.com/en-us/azure/event-hubs/event-hubs-create). Under your Event Hubs, create an Event Hub named `myhub`. We will use it in the function example.
- An Azure Cosmos DB. Under your Cosmos DB, create a database `db`, in the database `db`, create a collection `col`. We will use it in the function example.

#### Azure Resources and Environmen Variables
- Create following Azure Resources:
    - Azure Storage - For testing Blob and Queue triggers
        - Add environment variable `AzureWebJobsStorage` with value Azure Storage Connection String
    - Azure Cosmos DB
        - Add environment variable `AzureWebJobsCosmosDBConnectionString` with value Azure CosmosDB Connection String
        - Create Database : `ItemDb`
    - Azure EventHubs
        - Add environment variable `AzureWebJobsEventHubReceiver` with value Azure EventHub Connection String
        - Add environment variable `AzureWebJobsEventHubSender` with value Azure EventHub Connection String
        - Create following Event Hubs
            - `test-eventhuboutput-java`
            - `test-input-java`
            - `test-eventhuboutputjson-java`
            - `test-inputjson-java`
            - `test-eventhuboutputone-java`
            - `test-inputOne-java`
    - Azure Service Bus
        -  Add environment variable `AzureWebJobsServiceBus` with value Azure Service Connection String
        -  Add environment variable `SBTopicName` with value Service Bus Topic Name
        -  Add environment variable `SBTopicSubName` with value Service Bus Topic Subscription Name
        -  Add environment variable `SBQueueName` with value Service Bus Queue Name


#### Table Input and Output (Manual tests)
1. Open `Azure Storage Explorer`, find your Azure Storage Account. Under `Tables` create a table named `Person`. Under `Queues` create a queue named `mytablequeue`.
2. In table `Person`, add an entity with `PartitionKey=firstPartition, RowKey=firstRow, Name=firstName`.
3. In queue `mytablequeue`, add a message `firstRow`, then the Table Input function will get triggered.
1. Copy the `tableOutput` url from log, then use postman send a POST request with a name, let's say `second`, in body to this url, the Table Output function will get triggered.


#### Service Bus Queue Trigger and Output
1. Open Azure Portal in web browser, find your Service Bus Namespace. Create a Queue named `mysbqueue` if it doesn't exist.
2. Copy the `ServiceBusQueueOutput` url from log, then use postman send a POST request with a message in the body to this url, the Event Hub Output function will get triggered.
3. When Service Bus Queue Output function get triggered and add a message into `mysbqueue`, Service Bus Queue Trigger function will get triggered.

#### Service Bus Topic Trigger and Output
1. Open Azure Portal in web browser, find your Service Bus Namespace. Create a Topic named `mysbtopic` if it doesn't exist. Create a subscription named `mysubs` under `mysbtopic`.
2. Copy the `serviceBusTopicOutput` url from log, then use postman send a POST request with a message in the body to this url, the Event Hub Output function will get triggered.
3. When Service Bus Topic Output function get triggered and add a message into `mysbtopic`, Service Bus Topic Trigger function will get triggered.

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
   
### Notes for ExtensionBundle
1. In the host file, the extensionBundle part is important, if this part is not there, then when running end2end test, there will be all resources (eventhub, evnetgrid, servicebus…) not registered exception. Also, make sure the version of extensionBundle is always up-to-date. 
2. Another option to replace extensionBundle is to have file extensions.csproj, if you have this file, then the extensionbundle part can be removed from host.json. What this file do is to create a bin folder under /home/site/wwwroot with all the extensions you are going to need to work with those resources.
   Notes: make sure the version of the ExtensionsMetadataGenerator in extensions.csproj is update-to-date. 