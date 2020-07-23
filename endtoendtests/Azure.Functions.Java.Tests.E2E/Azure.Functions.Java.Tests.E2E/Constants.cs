﻿// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;

namespace Azure.Functions.Java.Tests.E2E
{
    public static class Constants
    {
        public static string FunctionsHostUrl = Environment.GetEnvironmentVariable("FunctionAppUrl") ?? "http://localhost:7071";
        public static string StorageConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsStorage");

        //Queue tests
        public static string OutputBindingQueueName = "test-output-java";
        public static string InputBindingQueueName = "test-input-java";
        public static string OutputBindingQueueNamePOJO = "test-output-java-pojo";
        public static string InputBindingQueueNamePOJO = "test-input-java-pojo";
        public static string InputBindingQueueNameMetadata = "test-input-java-metadata";
        public static string OutputBindingQueueNameMetadata = "test-output-java-metadata";
        public static string TestQueueMessage = "Hello, World";

        //Blob tests
        public static string TriggerInputBindingBlobContainer = "test-triggerinput-java";
        public static string InputBindingBlobContainer = "test-input-java";
        public static string OutputBindingBlobContainer = "test-output-java";

        // CosmosDB tests
        public static string CosmosDBConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsCosmosDBConnectionString");
        public static string DocDbDatabaseName = "ItemDb";
        public static string InputDocDbCollectionName = "ItemCollectionIn";
        public static string OutputDocDbCollectionName = "ItemCollectionOut";
        public static string DocDbLeaseCollectionName = "leases";
        public static string InputItemsCollectionName = "ItemsCollectionIn";
        public static string OutputItemsCollectionName = "ItemsCollectionOut";
        public static string LeaseItemsCollectionName = "Itemsleases";

        // EventHubs
        public static string OutputEventHubQueueName = "test-eventhuboutput-java";
        public static string InputEventHubName = "test-input-java";

        public static string OutputJsonEventHubQueueName = "test-eventhuboutputjson-java";
        public static string InputJsonEventHubName = "test-inputjson-java";

        public static string OutputOneEventHubQueueName = "test-eventhuboutputone-java";
        public static string InputCardinalityOneEventHubName = "test-inputOne-java";

        public static string OutputBinaryOneQueueName = "test-binary-output-cardinality-one-java";
        public static string InputBinaryOneEventHubQueueName = "test-binary-input-cardinality-one-java";

        public static string OutputBinaryManyQueueName = "test-binary-output-cardinality-many-list-java";
        public static string InputBinaryManyEventHubQueueName = "test-binary-input-cardinality-many-list-java";

        public static string OutputBinaryArrayManyQueueName = "test-binary-output-cardinality-many-array-java";
        public static string InputBinaryManyArrayEventHubQueueName = "test-binary-input-cardinality-many-array-java";

        // Kafka
        public static string OutputStringOneKafkaQueueName = "test-kafka-output-cardinality-one-java";

        // EventGrid
        public static string EventGridStorageOutputBindingQueueName = "test-eventgrid-output-binding-queue-java";

        // Settings
        public static string EventHubsConnectionStringSenderSetting = Environment.GetEnvironmentVariable("AzureWebJobsEventHubSender");
        public static string EventHubsConnectionStringSenderSetting2 = Environment.GetEnvironmentVariable("AzureWebJobsEventHubSender_2");

        public static string EventHubsConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsEventHubSender");

        // Xunit Fixtures and Collections
        public const string FunctionAppCollectionName = "FunctionAppCollection";
    }
}
