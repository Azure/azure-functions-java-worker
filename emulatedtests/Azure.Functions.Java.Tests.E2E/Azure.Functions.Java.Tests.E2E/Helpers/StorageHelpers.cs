// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Microsoft.WindowsAzure.Storage;
using Microsoft.WindowsAzure.Storage.Blob;
using Microsoft.WindowsAzure.Storage.Queue;
using System;
using System.Collections.Generic;
using System.IO;
using System.Threading.Tasks;

namespace Azure.Functions.Java.Tests.E2E
{
    class StorageHelpers
    {
        public static CloudStorageAccount _storageAccount = CloudStorageAccount.Parse(Constants.StorageConnectionStringSetting);
        public static CloudQueueClient _queueClient = _storageAccount.CreateCloudQueueClient();
        public static CloudBlobClient _cloudBlobClient = _storageAccount.CreateCloudBlobClient();

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

        public async static Task<string> InsertIntoQueue(string queueName, string queueMessage)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            await queue.CreateIfNotExistsAsync();
            CloudQueueMessage message = new CloudQueueMessage(queueMessage);            
            await queue.AddMessageAsync(message);
            return message.Id;
        }

        public async static Task<string> ReadFromQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            CloudQueueMessage retrievedMessage = null;
            await Utilities.RetryAsync(async () =>
            {
                retrievedMessage = await queue.GetMessageAsync();
                return retrievedMessage != null;
            }, pollingInterval: 4000);
            await queue.DeleteMessageAsync(retrievedMessage);
            return retrievedMessage.AsString;
        }

        public async static Task<IEnumerable<string>> ReadMessagesFromQueue(string queueName)
        {
            CloudQueue queue = _queueClient.GetQueueReference(queueName);
            IEnumerable<CloudQueueMessage> retrievedMessages = null;
            List<string> messages = new List<string>();
            await Utilities.RetryAsync(async () =>
            {
                retrievedMessages = await queue.GetMessagesAsync(3);
                return retrievedMessages != null;
            });
            foreach(CloudQueueMessage msg in retrievedMessages)
            {
                messages.Add(msg.AsString);
                await queue.DeleteMessageAsync(msg);
            }
            return messages;
        }


        public async static Task ClearBlobContainers()
        {
            await ClearBlobContainer(Constants.TriggerInputBindingBlobContainer);
            await ClearBlobContainer(Constants.InputBindingBlobContainer);
            await ClearBlobContainer(Constants.OutputBindingBlobContainer);
            await ClearBlobContainer(Constants.TriggerInputBindingBlobClientSdk);
            await ClearBlobContainer(Constants.TriggerInputBindingBlobContainerClientSdk);
        }

        public async static Task CreateBlobContainers()
        {
            await CreateBlobContainer(Constants.TriggerInputBindingBlobContainer);
            await CreateBlobContainer(Constants.InputBindingBlobContainer);
            await CreateBlobContainer(Constants.OutputBindingBlobContainer);
            await CreateBlobContainer(Constants.TriggerInputBindingBlobClientSdk);
            await CreateBlobContainer(Constants.TriggerInputBindingBlobContainerClientSdk);
        }

        public async static Task UpdloadFileToContainer(string containerName, string expectedFileName)
        {
            string sourceFile = $"{expectedFileName}.txt";
            File.WriteAllText(sourceFile, "Hello World");
            CloudBlobContainer cloudBlobContainer = _cloudBlobClient.GetContainerReference(containerName);
            CloudBlockBlob cloudBlockBlob = cloudBlobContainer.GetBlockBlobReference(sourceFile);
            await cloudBlockBlob.UploadFromFileAsync(sourceFile);
        }

        public async static Task<string> DownloadFileFromContainer(string containerName, string expectedFileName)
        {
            string destinationFile = $"{expectedFileName}_DOWNLOADED.txt";
            string sourceFile = $"{expectedFileName}.txt";
            CloudBlobContainer cloudBlobContainer = _cloudBlobClient.GetContainerReference(containerName);
            CloudBlockBlob cloudBlockBlob = cloudBlobContainer.GetBlockBlobReference(sourceFile);
            await Utilities.RetryAsync(async () =>
            {
               return await cloudBlockBlob.ExistsAsync();
            }, pollingInterval: 4000, timeout: 120 * 1000);
            await cloudBlockBlob.DownloadToFileAsync(destinationFile, FileMode.Create);
            return File.ReadAllText(destinationFile);
        }


        private static async Task<CloudBlobContainer> CreateBlobContainer(string containerName)
        {
            BlobContainerPermissions permissions = new BlobContainerPermissions
            {
                PublicAccess = BlobContainerPublicAccessType.Blob
            };
            CloudBlobContainer cloudBlobContainer = _cloudBlobClient.GetContainerReference(containerName);
            await cloudBlobContainer.CreateIfNotExistsAsync();
            await cloudBlobContainer.SetPermissionsAsync(permissions);
            return cloudBlobContainer;
        }

        private static async Task ClearBlobContainer(string containerName)
        {
            CloudBlobContainer cloudBlobContainer = _cloudBlobClient.GetContainerReference(containerName);
            BlobContinuationToken blobContinuationToken = null;
            do
            {
                if (!await cloudBlobContainer.ExistsAsync()) { continue; }
                var results = await cloudBlobContainer.ListBlobsSegmentedAsync(null, blobContinuationToken);
                // Get the value of the continuation token returned by the listing call.
                blobContinuationToken = results.ContinuationToken;
                foreach (IListBlobItem item in results.Results)
                {
                    Console.WriteLine(item.Uri);
                    String blobName = System.IO.Path.GetFileName(item.Uri.AbsolutePath);
                    CloudBlob cloudBlob = cloudBlobContainer.GetBlobReference(blobName);
                    await cloudBlob.DeleteIfExistsAsync();
                }
            } while (blobContinuationToken != null); // Loop while the continuation token is not null.             
        }
    }
}
