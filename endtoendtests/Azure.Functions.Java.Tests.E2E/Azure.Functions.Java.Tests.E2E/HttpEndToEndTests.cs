// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System.Net;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    public class HttpEndToEndTests 
    {
        [Theory]
        [InlineData("HttpTriggerJava", "?&name=Test", HttpStatusCode.OK, "Test")]
        [InlineData("HttpTriggerJavaMetadata", "?&firstName=John&lastName=Doe", HttpStatusCode.OK, "JohnDoe")]
        [InlineData("HttpTriggerJavaThrows", "", HttpStatusCode.InternalServerError, "")]
        [InlineData("HttpTriggerJava", "", HttpStatusCode.BadRequest, "Please pass a name on the query string or in the request body")]
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
    }
}
