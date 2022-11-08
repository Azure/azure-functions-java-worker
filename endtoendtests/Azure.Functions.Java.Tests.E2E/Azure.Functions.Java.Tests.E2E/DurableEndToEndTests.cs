// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;
using System;
using System.Net;
using System.Threading.Tasks;
using Xunit;
using System.Threading;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class DurableEndToEndTests
    {
        private readonly FunctionAppFixture _fixture;

        public DurableEndToEndTests(FunctionAppFixture fixture)
        {
            _fixture = fixture;
        }

        [Fact]
        public async Task Durable_OrchestrationCompletes()
        {
            JObject result = await Utilities.StartOrchestration("StartOrchestration", HttpStatusCode.Created);
            Assert.NotNull(result);

            String statusUrl = result["statusQueryGetUri"].ToString();

            int retryCount = 15;
            bool success = false;
            while(retryCount > 0)
            {

                result = await Utilities.InvokeUri(statusUrl);
                string runtimeStatus = result["runtimeStatus"].ToString();
                Console.WriteLine($"Orchestration is {runtimeStatus}");

                if (runtimeStatus.Equals("Completed")) {
                    success = true;
                    break;
                }

                Thread.Sleep(TimeSpan.FromSeconds(1));
                retryCount -= 1;
            }

            Assert.True(success);
        }
    }
}
