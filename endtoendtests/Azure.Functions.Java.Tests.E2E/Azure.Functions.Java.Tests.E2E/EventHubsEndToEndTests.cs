// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Reflection.Metadata;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class EventHubsEndToEndTests 
    {
        private readonly FunctionAppFixture _fixture;

        public EventHubsEndToEndTests(FunctionAppFixture fixture)
        {
           _fixture = fixture;
        }

        [Fact]
        public async Task EventHubTriggerAndOutputJSON_EventHubOutputJson_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputJsonEventHubQueueName);

                // Need to setup EventHubs: test-inputjson-java and test-outputjson-java
                await EventHubQueueHelpers.SendJSONMessagesAsync(expectedEventId, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputJsonEventHubQueueName);
                JObject json = JObject.Parse(queueMessage);
                Assert.Contains(expectedEventId, json["value"].ToString());
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputJsonEventHubQueueName);
            }
        }

        [Fact]
        public async Task EventHubTriggerAndOutputString_EventHubOutput_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputEventHubQueueName);

                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.InputEventHubName, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputEventHubQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputEventHubQueueName);
            }
        }

        [Fact]
        public async Task EventHubTriggerCardinalityOne_EventHubOutputInputOne_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputOneEventHubQueueName);

                // Need to setup EventHubs: test-inputOne-java and test-outputone-java
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.InputCardinalityOneEventHubName, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                IEnumerable<string> queueMessages = await StorageHelpers.ReadMessagesFromQueue(Constants.OutputOneEventHubQueueName);
                Assert.True(queueMessages.All(msg => msg.Contains(expectedEventId)));
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputOneEventHubQueueName);
            }
        }

        
        [Fact]
        public async Task EventHubTriggerAndOutputBinaryCardinalityManyListBinary_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryManyQueueName);

                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryManyEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBinaryManyQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputEventHubQueueName);
            }
        }
        
        [Fact]
       public async Task EventHubTriggerAndOutputBinaryCardinalityOne_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryOneQueueName);

                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryOneEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBinaryOneQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputEventHubQueueName);
            }
        }
        
        [Fact]
        public async Task EventHubTriggerAndOutputBinaryCardinalityManyArrayBinary_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryArrayManyQueueName);

                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryManyArrayEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputBinaryArrayManyQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputEventHubQueueName);
            }
        }

        [Fact]
        public async Task EventHubOutputFixedDelayRetry()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.FixedDelayRetry);

                //send message to eventhub
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.FixedDelayRetry, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.FixedDelayRetry);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.FixedDelayRetry);
            }
        }

        [Fact]
        public async Task EventHubOutputExponentialBackoffRetry()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.ExponentialBackoffRetry);

                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.ExponentialBackoffRetry, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.ExponentialBackoffRetry);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.ExponentialBackoffRetry);
            }
        }

        [Fact]
        public async Task EventHubTriggerRetryContextCount()
        {
            string expectedRetryCount = "1";
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.RetryCount);

                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.RetryCount, Constants.EventHubsConnectionStringSenderSetting);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.RetryCount);
                Assert.Equal(expectedRetryCount, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.RetryCount);
            }
        }

        [Fact]
        public async Task EventHubTriggerMaxRetryContextCount()
        {
            string expectedMaxRetryCount = "3";
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.MaxRetryCount);

                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubQueueHelpers.SendMessagesAsync(expectedEventId, Constants.MaxRetryCount, Constants.EventHubsConnectionStringSenderSetting2);

                //Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.MaxRetryCount);
                Assert.Equal(expectedMaxRetryCount, queueMessage);
            }
            finally
            {
                //Clear queue
                await StorageHelpers.ClearQueue(Constants.MaxRetryCount);
            }
        }

        private static async Task SetupQueue(string queueName)
        {
            //Clear queue
            await StorageHelpers.ClearQueue(queueName);

            //Set up and trigger            
            await StorageHelpers.CreateQueue(queueName);
        }
    }
}
