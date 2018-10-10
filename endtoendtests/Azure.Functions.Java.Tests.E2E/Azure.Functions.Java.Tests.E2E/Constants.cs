﻿// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;

namespace Azure.Functions.Java.Tests.E2E
{
    public static class Constants
    {
        public static string FunctionsHostUrl = "http://localhost:7071";
        public static string StorageConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsStorage");

        //Queue tests
        public static string OutputBindingQueueName = "test-output-java";
        public static string InputBindingQueueName = "test-input-java";
        public static string OutputBindingQueueNamePOJO = "test-output-java-pojo";
        public static string InputBindingQueueNamePOJO = "test-input-java-pojo";
        public static string TestQueueMessage = "Hello, World";

        // CosmosDB tests
        public static string CosmosDBConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsCosmosDBConnectionString");
        public static string DocDbDatabaseName = "ItemDb";
        public static string InputDocDbCollectionName = "ItemCollectionIn";
        public static string OutputDocDbCollectionName = "ItemCollectionOut";
        public static string DocDbLeaseCollectionName = "leases";
        public static string InputItemsCollectionName = "ItemsCollectionIn";
        public static string OutputItemsCollectionName = "ItemsCollectionOut";
        public static string LeaseItemsCollectionName = "Itemsleases";
    }
}
