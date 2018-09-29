// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Microsoft.WindowsAzure.Storage;
using Microsoft.WindowsAzure.Storage.Queue;
using System.Threading.Tasks;

namespace Azure.Functions.Java.Tests.E2E
{
    class StorageHelpers
    {
        public static CloudStorageAccount _storageAccount = CloudStorageAccount.Parse(Constants.StorageConnectionStringSetting);
        public static CloudQueueClient _queueClient = _storageAccount.CreateCloudQueueClient();

        public async static Task DeleteQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            await queue.DeleteAsync();
        }

        public async static Task ClearQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            if (await queue.ExistsAsync())
            {
                await queue.ClearAsync();
            }
        }

        public async static Task CreateQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            await queue.CreateIfNotExistsAsync();
        }

        public async static Task InsertIntoQueue(string queueName, string queueMessage)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            await queue.CreateIfNotExistsAsync();
            CloudQueueMessage message = new CloudQueueMessage(queueMessage);
            await queue.AddMessageAsync(message);
        }

        public async static Task<string> ReadFromQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            CloudQueueMessage retrievedMessage = null;
            await Utilities.RetryAsync(async () =>
            {
                retrievedMessage = await queue.GetMessageAsync();
                return retrievedMessage != null;
            });
            await queue.DeleteMessageAsync(retrievedMessage);
            return retrievedMessage.AsString;
        }
    }
}
