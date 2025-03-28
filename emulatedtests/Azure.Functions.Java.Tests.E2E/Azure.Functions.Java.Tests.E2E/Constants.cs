// Copyright (c) .NET Foundation. All rights reserved.
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
        public static string TriggerInputBindingBlobContainer = "test-triggerinput-java-new";
        public static string InputBindingBlobContainer = "test-input-java-new";
        public static string OutputBindingBlobContainer = "test-output-java-new";
        public static string TriggerInputBindingBlobClientSdk = "test-triggerinput-blobclient";
        public static string TriggerInputBindingBlobContainerClientSdk = "test-triggerinput-blobcontclient";

        // Xunit Fixtures and Collections
        public const string FunctionAppCollectionName = "FunctionAppCollection";
    }
}
