# Microsoft Azure Functions in Java

This repo contains several Java Azure Functions samples for different events.

## Prerequisites
- JDK 1.8
- Maven 3.0+
- [.NET Core SDK](https://www.microsoft.com/net/learn/get-started/windows)
- [Azure Functions Core Tools v2](https://docs.microsoft.com/en-us/azure/azure-functions/functions-run-local#v2)
- An active azure subscription
- [Azure Storage Explorer](https://azure.microsoft.com/en-us/features/storage-explorer/)

## Sample List and Test Status
| Sample | Event | Binding | Test Status | - |
| ------ | ---------- | ----------- | -------- | -- |
| [HTTP](HTTP) | HTTP  | HttpTrigger | `pass` | [README](HTTP/README.md) |
| [Timer](Timer) | Timer | TimerTrigger | `pass` | [README](Timer/README.md) |
| Blob | Storage Blob | BlobTrigger <br> BlobInput <br> BlobOutput | `-` <br> `-` <br> `-` | README |
| [Queue](Queue) | Storage Queue | QueueTrigger <br> QueueOutput | `pass` <br> `pass` | [README](Queue/README.md) |
| Table | Storage Table | TableInput <br> Table Output| `-` <br> `-`| README |
| CosmosDB | Cosmos DB | CosmosDBTrigger <br> CosmosDBInput <br> CosmosDBOutput| `-` <br> `-` <br> `-`| README |
| ServiceBusQueue | Service Bus Queue | ServiceBusQueueTrigger <br> ServiceBusQueueOutput | `-` <br> `-` | README |
| ServiceBusTopic | Service Bus Topic | ServiceBusTopicTrigger <br> ServiceBusTopicOutput | `-` <br> `-` | README |
| EventHubs | Event Hubs | EventHubsTrigger <br> EventHubsOutput | `-` <br> `-` | README |
| EventGrid | Event Grid | EventGridTrigger | `-` | README |


