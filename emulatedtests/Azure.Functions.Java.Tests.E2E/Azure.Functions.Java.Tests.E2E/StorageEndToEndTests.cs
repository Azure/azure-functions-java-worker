// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class StorageEndToEndTests 
    {
        private readonly FunctionAppFixture _fixture;

        public StorageEndToEndTests(FunctionAppFixture fixture)
        {
            _fixture = fixture;
        }

        [Fact]
        public async Task QueueTrigger_QueueOutput_Succeeds()
        {
            string expectedQueueMessage = Guid.NewGuid().ToString();
            //Clear queue
            await StorageHelpers.ClearQueue(Constants.OutputBindingQueueName);
            await StorageHelpers.ClearQueue(Constants.InputBindingQueueName);

            //Set up and trigger            
            await StorageHelpers.CreateQueue(Constants.OutputBindingQueueName);
            await StorageHelpers.InsertIntoQueue(Constants.InputBindingQueueName, expectedQueueMessage);
            
            //Verify
            var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBindingQueueName);
            Assert.Equal(expectedQueueMessage, queueMessage);
        }

        [Fact]
        public async Task QueueTrigger_BindToTriggerMetadata_Succeeds()
        {
            string inputQueueMessage = Guid.NewGuid().ToString();
            //Clear queue
            await StorageHelpers.ClearQueue(Constants.OutputBindingQueueNameMetadata);
            await StorageHelpers.ClearQueue(Constants.InputBindingQueueNameMetadata);

            //Set up and trigger            
            await StorageHelpers.CreateQueue(Constants.OutputBindingQueueNameMetadata);
            string expectedQueueMessage = await StorageHelpers.InsertIntoQueue(Constants.InputBindingQueueNameMetadata, inputQueueMessage);

            //Verify
            var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBindingQueueNameMetadata);
            Assert.Contains(expectedQueueMessage, queueMessage);
        }


        [Fact]
        public async Task QueueTrigger_QueueOutput_POJO_Succeeds()
        {
            string expectedQueueMessage = Guid.NewGuid().ToString();
            //Clear queue
            await StorageHelpers.ClearQueue(Constants.OutputBindingQueueNamePOJO);
            await StorageHelpers.ClearQueue(Constants.InputBindingQueueNamePOJO);

            //Set up and trigger            
            await StorageHelpers.CreateQueue(Constants.OutputBindingQueueNamePOJO);
            JObject testData = new JObject();
            testData["id"] = expectedQueueMessage;
            await StorageHelpers.InsertIntoQueue(Constants.InputBindingQueueNamePOJO, testData.ToString());

            //Verify
            var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBindingQueueNamePOJO);
            Assert.Contains(expectedQueueMessage, queueMessage);
        }

        [Fact]
        public async Task QueueOutput_POJOList_Succeeds()
        {
            string expectedQueueMessage = Guid.NewGuid().ToString();
            //Clear queue
            await StorageHelpers.ClearQueue(Constants.OutputBindingQueueNamePOJO);

            //Trigger
            Assert.True(await Utilities.InvokeHttpTrigger("QueueOutputPOJOList", $"?queueMessageId={expectedQueueMessage}", HttpStatusCode.OK, expectedQueueMessage));

            //Verify
            IEnumerable<string> queueMessages = await StorageHelpers.ReadMessagesFromQueue(Constants.OutputBindingQueueNamePOJO);
            Assert.True(queueMessages.All(msg => msg.Contains(expectedQueueMessage)));
        }

        [Fact]
        public async Task BlobTriggerToBlob_Succeeds()
        {
            string fileName = Guid.NewGuid().ToString();

            //cleanup
            await StorageHelpers.ClearBlobContainers();

            //Setup
            await StorageHelpers.CreateBlobContainers();
            await StorageHelpers.UpdloadFileToContainer(Constants.InputBindingBlobContainer, fileName);

            //Trigger
            await StorageHelpers.UpdloadFileToContainer(Constants.TriggerInputBindingBlobContainer, fileName);

            //Verify
            string result = await StorageHelpers.DownloadFileFromContainer(Constants.OutputBindingBlobContainer, fileName);

            Assert.Equal("Hello World", result);
        }

        [Fact]
        [Trait("Category", "SdkTypes")]
        public async Task BlobTriggerToBlob_BlobClient_Succeeds()
        {
            string fileName = Guid.NewGuid().ToString();

            //cleanup
            await StorageHelpers.ClearBlobContainers();

            //Setup
            await StorageHelpers.CreateBlobContainers();

            //Trigger
            await StorageHelpers.UpdloadFileToContainer(Constants.TriggerInputBindingBlobClientSdk, fileName);

            //Verify
            string result = await StorageHelpers.DownloadFileFromContainer(Constants.OutputBindingBlobContainer, "testfile");
            Assert.Equal("Hello World", result);
        }

        [Fact]
        [Trait("Category", "SdkTypes")]
        public async Task BlobTriggerToBlob_BlobContainerClient_Succeeds()
        {
            string fileName = Guid.NewGuid().ToString();

            //cleanup
            await StorageHelpers.ClearBlobContainers();

            //Setup
            await StorageHelpers.CreateBlobContainers();

            //Trigger
            await StorageHelpers.UpdloadFileToContainer(Constants.TriggerInputBindingBlobContainerClientSdk, fileName);

            //Verify
            string result = await StorageHelpers.DownloadFileFromContainer(Constants.OutputBindingBlobContainer, "testfile");
            Assert.Equal("Hello World", result);
        }
    }
}
