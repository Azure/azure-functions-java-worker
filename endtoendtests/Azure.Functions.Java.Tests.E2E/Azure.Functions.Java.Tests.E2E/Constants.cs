using System;

namespace Azure.Functions.Java.Tests.E2E
{
    public static class Constants
    {
        public static string FunctionsHostUrl = "http://localhost:5000";
        public static string StorageConnectionStringSetting = Environment.GetEnvironmentVariable("AzureWebJobsStorage");
        public static string OutputBindingQueueName = "test-output-java";
        public static string InputBindingQueueName = "test-input-java";
        public static string TestQueueMessage = "Hello, World";
    }
}
