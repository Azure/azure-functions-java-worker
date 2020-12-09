// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Net;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class EventGridEndToEndTests
    {
        private readonly FunctionAppFixture _fixture;

        public EventGridEndToEndTests(FunctionAppFixture fixture)
        {
            _fixture = fixture;
        }

        [Fact]
        public async Task TestEventGridTrigger()
        {
            /* Testing EventGrid Triggers
             * 
             * To test EventGrid triggers - we send in a http message to function with 
             * EventGrid trigger and expect a 202 back. The header specifies EG that
             * it is a `Notification`.
             */

            string json = @"{
  'topic': ' /subscriptions/SomSub/resourcegroups/SomeRG/providers/Microsoft.EventHub/namespaces/testeventhub',
  'subject': 'eventhubs/test',
  'eventType': 'captureFileCreated',
  'eventTime': '2017-07-14T23:10:27.7689666Z',
  'id': '7b11c4ce-0000-0000-9999-1730e766f126',
  'data': {
    'fileUrl': 'https://test.blob.core.windows.net/debugging/testblob.txt',
    'fileType': 'AzureBlockBlob',
    'partitionId': '1',
    'sizeInBytes': 0,
    'eventCount': 0,
    'firstSequenceNumber': -1,
    'lastSequenceNumber': -1,
    'firstEnqueueTime': '0001-01-01T00:00:00',
    'lastEnqueueTime': '0001-01-01T00:00:00'
  },
  'dataVersion': '', 
  'metadataVersion': '1'
}";
            // Create json for body
            var content = JObject.Parse(json);
            Console.WriteLine($"TestEventGridTrigger's JSON Content: {content}");
            Assert.True(await Utilities.InvokeEventGridTrigger("EventGridTriggerJava", content));
        }

        [Fact]
        public async Task TestEventGridOutputBinding()
        {
            /* Testing EventGrid output binding
             * 
             * EventGrid function `EventGridOutputBindingJava` is 
             * triggered using the http trigger, passing in a guid to it, and an
             * event is sent to EventGrid. It forwards the event to queue and we
             * read the queue contents.
             * 
             * Verify that the following event grid and queue are created: 
             *  - Constants.EventGridOutputBindingTopicUriSetting (Azure Devops will have the information for this).
             *  - Constants.EventGridStorageOutputBindingQueueName
             *  
             *  Also verify that the queue is subscribed to the events in that eventgrid.
             */

            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                // clearing up queue for event grid to send event to.
                await SetupQueue(Constants.EventGridStorageOutputBindingQueueName);

                Assert.True(await Utilities.InvokeHttpTrigger("EventGridOutputBindingJava",
                    $"?&testuuid={expectedEventId}", HttpStatusCode.OK, null));

                var queueMessage = await StorageHelpers.ReadFromQueue(
                    Constants.EventGridStorageOutputBindingQueueName);

                Assert.Contains(expectedEventId, queueMessage);
            }
            finally
            {
                // Clear queue for next run
                await StorageHelpers.ClearQueue(Constants.EventGridStorageOutputBindingQueueName);
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
