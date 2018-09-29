// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    public class EndToEndTests 
    {
        [Fact]
        public async Task HttpTrigger_Succeeds()
        {
           await InvokeHttpTrigger("HttpTrigger");
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
        public async Task CosmosDBTrigger_CosmosDBOutput_Succeeds()
        {
            string expectedDocId = Guid.NewGuid().ToString();
            try
            {
                //Setup
                await CosmosDBHelpers.CreateDocumentCollections();

                //Trigger            
                await CosmosDBHelpers.CreateDocument(expectedDocId);

                //Read
                var documentId = await CosmosDBHelpers.ReadDocument(expectedDocId);
                Assert.Equal(expectedDocId, documentId);
            }
            finally
            {
                //Clean up
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId);
            }
        }

        private async Task InvokeHttpTrigger(string functionName)
        {
            // TODO: Get function key
            //string functionKey = await _fixture.Host.GetFunctionSecretAsync($"{functionName}");
            //string uri = $"api/{functionName}?code={functionKey}&name=Mathew";

            string uri = $"api/{functionName}?&name=Mathew";
            HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/plain"));

            var httpClient = new HttpClient();
            httpClient.BaseAddress = new Uri(Constants.FunctionsHostUrl);
            var response = await httpClient.SendAsync(request);
            Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        }
    }
}
