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
        public async Task EventHubTriggerAndOutputJSON_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputJsonEventHubQueueName);

                // Need to setup EventHubs: test-inputjson-java and test-outputjson-java
                await EventHubsHelpers.SendJSONMessagesAsync(expectedEventId, Constants.EventHubsConnectionStringSenderSetting);

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
        public async Task EventHubTriggerAndOutputString_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputEventHubQueueName);

                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubsHelpers.SendMessagesAsync(expectedEventId, Constants.InputEventHubName, Constants.EventHubsConnectionStringSenderSetting);

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
        public async Task EventHubTriggerCardinalityOne_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputOneEventHubQueueName);

                // Need to setup EventHubs: test-inputOne-java and test-outputone-java
                await EventHubsHelpers.SendMessagesAsync(expectedEventId, Constants.InputCardinalityOneEventHubName, Constants.EventHubsConnectionStringSenderSetting);

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

        /*
        [Fact]
        public async Task EventHubTriggerAndOutputBinaryListMany_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryManyQueueName);

                await EventHubsHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryManyEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

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
        */
        [Fact]
       public async Task EventHubTriggerAndOutputBinaryOne_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryOneQueueName);

                await EventHubsHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryOneEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

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
        /*
        [Fact]
        public async Task EventHubTriggerAndOutputBinaryArrayMany_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                await SetupQueue(Constants.OutputBinaryArrayManyQueueName);

                await EventHubsHelpers.SendMessagesAsync(expectedEventId, Constants.InputBinaryManyArrayEventHubQueueName, Constants.EventHubsConnectionStringSenderSetting2);

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
        */
       

        private static async Task SetupQueue(string queueName)
        {
            //Clear queue
            await StorageHelpers.ClearQueue(queueName);

            //Set up and trigger            
            await StorageHelpers.CreateQueue(queueName);
        }
    }
}
