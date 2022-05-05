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

        // TODO: remove commented test cases
        [Theory]
        [InlineData("HttpTriggerJava", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("FontTypeSupport", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpTriggerJavaMetadata", "?&firstName=John&lastName=Doe", HttpStatusCode.OK, "JohnDoe")]
        [InlineData("HttpTriggerJavaThrows", "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpTriggerJava", "", HttpStatusCode.BadRequest, "Please pass a name on the query string or in the request body")]
        //[InlineData("HttpExample-runRetryFail", "", HttpStatusCode.InternalServerError, "")]
        //[InlineData("HttpExample-runExponentialBackoffRetryFail",  "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpTriggerWaitMethod", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpTriggerNotifyMethod", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpTriggerJavaVersion", "", HttpStatusCode.OK, "HttpTriggerJavaVersion")]
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

            Assert.True(await Utilities.InvokeHttpTrigger("HttpTriggerJavaClassLoader", "?&name=Test", HttpStatusCode.OK, "Test"));

        }

        [Fact]
        public async void HttpTriggerJavaStatic()
        {
            await HttpTriggerTests("HttpTriggerJavaStatic1", "", HttpStatusCode.OK, "1");
            await HttpTriggerTests("HttpTriggerJavaStatic2", "", HttpStatusCode.OK, "2");
            await HttpTriggerTests("HttpTriggerJavaStatic1", "", HttpStatusCode.OK, "3");
            await HttpTriggerTests("HttpTriggerJavaStatic2", "", HttpStatusCode.OK, "4");
        }

        [Fact]
        public async Task HttpTrigger_BindingName()
        {
            Assert.True(await Utilities.InvokeHttpTrigger("BindingName", "/testMessage", HttpStatusCode.OK, "testMessage"));
        }
    }
}
