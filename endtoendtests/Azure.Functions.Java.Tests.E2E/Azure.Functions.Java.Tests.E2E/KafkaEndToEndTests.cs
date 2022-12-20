//using Newtonsoft.Json.Linq;
//using System;
//using System.Collections.Generic;
//using System.Linq;
//using System.Reflection.Metadata;
//using System.Threading.Tasks;
//using Xunit;
//
//namespace Azure.Functions.Java.Tests.E2E
//{
//    [Collection(Constants.FunctionAppCollectionName)]
//    public class KafkaEndToEndTests
//    {
//        [Fact]
//
//        public async Task KafkaTriggerAndOutputString_Succeeds()
//        {
//            string expectedEventId = Guid.NewGuid().ToString();
//            try
//            {
//                // Send Message to the Kafka Cluster using Kafka Output
//                await SetupQueue(Constants.OutputStringOneKafkaQueueName);
//                Assert.True(await Utilities.InvokeHttpTrigger("HttpTriggerAndKafkaOutput", $"?&message={expectedEventId}", System.Net.HttpStatusCode.OK, expectedEventId));
//                // Verify
//                var queueMessage = await StorageHelpers.ReadFromQueue(Constants.OutputStringOneKafkaQueueName);
//                Assert.Contains(expectedEventId, queueMessage);
//            }
//            finally
//            {
//                // Clear queue
//                await StorageHelpers.ClearQueue(Constants.OutputStringOneKafkaQueueName);
//            }
//        }
//        private static async Task SetupQueue(string queueName)
//        {
//            //Clear queue
//            await StorageHelpers.ClearQueue(queueName);
//
//            //Set up and trigger
//            await StorageHelpers.CreateQueue(queueName);
//        }
//    }
//}
