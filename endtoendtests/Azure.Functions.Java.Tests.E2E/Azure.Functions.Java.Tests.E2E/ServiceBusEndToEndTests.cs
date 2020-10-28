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
    public class ServiceBusEndToEndTests
    {
        private readonly FunctionAppFixture _fixture;

        public ServiceBusEndToEndTests(FunctionAppFixture fixture)
        {
            _fixture = fixture;
        }

        [Fact]
        public async Task ServiceBusTopicTrigger_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                // Send Message to the Service bus topic
                await SetupQueue(Constants.ServiceBusQueueTriggerTopicQueueName);
                await ServiceBusHelpers.SendMessagesAsync(expectedEventId, Constants.ServiceBusQueueTriggerServiceBusTopicName, Constants.ServiceBusConnectionStringSetting);
                // Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.ServiceBusQueueTriggerTopicQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            } 
            finally
            {
                // Clear queue
                await StorageHelpers.ClearQueue(Constants.ServiceBusQueueTriggerTopicQueueName);
            }
        }

        [Fact]
        public async Task serviceBusTopicBatchTrigger_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                // Send Message to the Service bus topic
                await SetupQueue(Constants.serviceBusTopicBatchTriggerQueueTopicName);
                await ServiceBusHelpers.SendMessagesAsync(expectedEventId, Constants.serviceBusTopicBatchTriggerServiceBusTopicName, Constants.ServiceBusConnectionStringSetting);
                // Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.serviceBusTopicBatchTriggerQueueTopicName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                // Clear queue
                await StorageHelpers.ClearQueue(Constants.serviceBusTopicBatchTriggerQueueTopicName);
            }
        }

        [Fact]
        public async Task ServiceBusQueueTriggerTests_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                // Send Message to the Service bus queue
                await SetupQueue(Constants.ServiceBusQueueTriggerQueueName);
                await ServiceBusHelpers.SendMessagesAsync(expectedEventId, Constants.ServiceBusQueueTriggerServiceBusName, Constants.ServiceBusConnectionStringSetting);
                // Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.ServiceBusQueueTriggerQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                // Clear queue
                await StorageHelpers.ClearQueue(Constants.ServiceBusQueueTriggerQueueName);
            }
        }

        [Fact]
        public async Task serviceBusQueueBatchTrigger_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                // Send Message to the Service bus queue
                await SetupQueue(Constants.serviceBusTopicBatchTriggerQueueName);
                await ServiceBusHelpers.SendMessagesAsync(expectedEventId, Constants.serviceBusTopicBatchTriggerServiceBusName, Constants.ServiceBusConnectionStringSetting);
                // Verify
                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.serviceBusTopicBatchTriggerQueueName);
                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                // Clear queue
                await StorageHelpers.ClearQueue(Constants.serviceBusTopicBatchTriggerQueueName);
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
