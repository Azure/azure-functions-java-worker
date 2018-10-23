// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;
using System.Net;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    public class CosmosDBEndToEndTests 
    {
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

        [Fact]
        public async Task CosmosDBInputId_Succeeds()
        {
            string expectedDocId = Guid.NewGuid().ToString();
            try
            {
                //Setup
                TestDocument testDocument = new TestDocument()
                {
                    id = expectedDocId,
                    name = "test"
                };
                await CosmosDBHelpers.CreateDocumentCollections();

                //Trigger            
                await CosmosDBHelpers.CreateDocument(testDocument);

                // Trigger and verify
                Assert.True(await Utilities.InvokeHttpTrigger("CosmosDBInputId", $"?&docId={expectedDocId}", HttpStatusCode.OK, expectedDocId));
            }
            finally
            {
                //Clean up
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId);
            }
        }

        [Fact]
        public async Task CosmosDBInputIdPOJO_Succeeds()
        {
            string expectedDocId = Guid.NewGuid().ToString();
            try
            {
                //Setup
                TestDocument testDocument = new TestDocument()
                {
                    id = expectedDocId,
                    name = "test"
                };
                await CosmosDBHelpers.CreateDocumentCollections();

                //Trigger            
                await CosmosDBHelpers.CreateDocument(testDocument);

                // Trigger and verify
                Assert.True(await Utilities.InvokeHttpTrigger("CosmosDBInputIdPOJO", $"?&docId={expectedDocId}", HttpStatusCode.OK, expectedDocId));
            }
            finally
            {
                //Clean up
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId);
            }
        }

        [Fact]
        public async Task CosmosDBInputSqlQuery_Succeeds()
        {
            string expectedDocId1 = Guid.NewGuid().ToString();
            string expectedDocId2 = Guid.NewGuid().ToString();
            string testName = "Joe";
            try
            {
                //Setup
                TestDocument testDocument1 = new TestDocument()
                {
                    id = expectedDocId1,
                    name = testName
                };

                TestDocument testDocument2 = new TestDocument()
                {
                    id = expectedDocId2,
                    name = testName
                };
                await CosmosDBHelpers.CreateDocumentCollections();
                await CosmosDBHelpers.CreateDocument(testDocument1);
                await CosmosDBHelpers.CreateDocument(testDocument2);

                // Trigger and verify
                Assert.True(await Utilities.InvokeHttpTrigger("CosmosDBInputQuery", "?&name=Joe", HttpStatusCode.OK, "Joe"));
            }
            finally
            {
                //Clean up
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId1);
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId2);
            }
        }

        [Fact]
        public async Task CosmosDBInputSqlQuery_POJOArrayAndList_Succeeds()
        {
            string expectedDocId1 = Guid.NewGuid().ToString();
            string expectedDocId2 = Guid.NewGuid().ToString();
            string testName = "Joe";
            try
            {
                //Setup
                TestDocument testDocument1 = new TestDocument()
                {
                    id = expectedDocId1,
                    name = testName
                };

                TestDocument testDocument2 = new TestDocument()
                {
                    id = expectedDocId2,
                    name = testName
                };
                await CosmosDBHelpers.CreateDocumentCollections();
                await CosmosDBHelpers.CreateDocument(testDocument1);
                await CosmosDBHelpers.CreateDocument(testDocument2);

                // Trigger and verify
                Assert.True(await Utilities.InvokeHttpTrigger("CosmosDBInputQueryPOJOArray", "?&name=Joe", HttpStatusCode.OK, "Joe"));
                Assert.True(await Utilities.InvokeHttpTrigger("CosmosDBInputQueryPOJOList", "?&name=Joe", HttpStatusCode.OK, "Joe"));
            }
            finally
            {
                //Clean up
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId1);
                await CosmosDBHelpers.DeleteTestDocuments(expectedDocId2);
            }
        }
    }
}
