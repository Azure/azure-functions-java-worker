// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    public class StorageEndToEndTests 
    {
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
    }
}
