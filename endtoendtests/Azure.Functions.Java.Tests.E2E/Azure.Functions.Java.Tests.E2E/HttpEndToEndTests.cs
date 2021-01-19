// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;
using System.Net;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class HttpEndToEndTests 
    {
        private readonly FunctionAppFixture _fixture;

        public HttpEndToEndTests(FunctionAppFixture fixture)
        {
            _fixture = fixture;
        }

        [Theory]
        [InlineData("HttpTriggerJava", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpTriggerJavaMetadata", "?&firstName=John&lastName=Doe", HttpStatusCode.OK, "JohnDoe")]
        [InlineData("HttpTriggerJavaThrows", "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpTriggerJava", "", HttpStatusCode.BadRequest, "Please pass a name on the query string or in the request body")]
        [InlineData("HttpExample-retry", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpExample-runRetryFail", "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpExample-runExponentialBackoffRetryFail",  "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpExample-runExponentialBackoffRetry", "?&name=Test", HttpStatusCode.OK, "Test")]
        public async Task HttpTriggerTests(string functionName, string queryString, HttpStatusCode expectedStatusCode, string expectedErrorMessage)
        {
            // TODO: Verify exception on 500 after https://github.com/Azure/azure-functions-host/issues/3589
            Assert.True(await Utilities.InvokeHttpTrigger(functionName, queryString, expectedStatusCode, expectedErrorMessage));
        }

        [Fact]
        public async Task HttpTrigger_ReturnsCustomCode()
        {
            Assert.True(await Utilities.InvokeHttpTrigger("HttpTriggerCustomCode", "?&name=Test",  HttpStatusCode.OK, "Test", 209));
        }

        [Fact]
        public async Task HttpTriggerJavaClassLoader()
        {
            // The e2e project has newer jars than the one we use in the worker.
            // The purpose of this test will be called for three scenarios:
            // 1. Java 11 -- Client code takes presence. -- works fine.
            // 2. Java 8 with no Application settings, worker lib jars takes presence -- throw exception
            // 3. Java 8 with with Application settings, worker lib jars takes presence -- works fine.

            String value = Environment.GetEnvironmentVariable("FUNCTIONS_WORKER_JAVA_LOAD_APP_LIBS");
            String java_home = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (java_home.Contains("zulu11") || (value != null && value.ToLower().Equals("true")))
            {
                Assert.True(await Utilities.InvokeHttpTrigger("HttpTriggerJavaClassLoader", "?&name=Test", HttpStatusCode.OK, "Test"));
            }
            else
            {
                Assert.True(await Utilities.InvokeHttpTrigger("HttpTriggerJavaClassLoader", "?&name=Test", HttpStatusCode.InternalServerError, ""));
            }
        }
    }
}
