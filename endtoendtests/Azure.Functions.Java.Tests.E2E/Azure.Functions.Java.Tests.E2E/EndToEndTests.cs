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
    public class EndToEndTests 
    {
        [Theory]
        [InlineData("HttpTriggerJava", "?&name=Test", HttpStatusCode.OK, "")]
        [InlineData("HttpTriggerJavaThrows", "", HttpStatusCode.InternalServerError, "Test Exception")]
        [InlineData("HttpTriggerJava", "", HttpStatusCode.BadRequest, "Please pass a name on the query string or in the request body")]
        public async Task HttpTriggerTests(string functionName, string queryString, HttpStatusCode expectedStatusCode, string expectedErrorMessage)
        {
            // TODO: Get function key
            //string functionKey = await _fixture.Host.GetFunctionSecretAsync($"{functionName}");
            //string uri = $"api/{functionName}?code={functionKey}&name=Mathew";
           
            string uri = $"api/{functionName}{queryString}";
            HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, uri);
            request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/plain"));

            var httpClient = new HttpClient();
            httpClient.BaseAddress = new Uri(Constants.FunctionsHostUrl);
            var response = await httpClient.SendAsync(request);
            Assert.Equal(expectedStatusCode, response.StatusCode);

            if (!string.IsNullOrEmpty(expectedErrorMessage))
            {
                string error = await response.Content.ReadAsStringAsync();
                Assert.Contains(expectedErrorMessage, error);
            }
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

       
    }
}
