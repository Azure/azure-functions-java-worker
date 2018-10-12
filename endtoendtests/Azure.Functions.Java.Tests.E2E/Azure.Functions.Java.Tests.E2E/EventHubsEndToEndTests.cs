// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    public class EventHubsEndToEndTests 
    {
        [Fact]
        public async Task EventHubTriggerAndOutput_Succeeds()
        {
            string expectedEventId = Guid.NewGuid().ToString();
            try
            {
                //Setup
                // Need to setup EventHubs: test-input-java and test-output-java
                await EventHubsHelpers.SendMessagesAsync(expectedEventId);

                //Clear queue
                await StorageHelpers.ClearQueue(Constants.OutputEventHubQueueName);

                //Set up and trigger            
                await StorageHelpers.CreateQueue(Constants.OutputEventHubQueueName);

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
    }
}
