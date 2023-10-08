// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Net;
using System.Threading;
using System.Threading.Tasks;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E
{
    [Collection(Constants.FunctionAppCollectionName)]
    public class SqlEndToEndTests
    {
        private readonly FunctionAppFixture _fixture;

        public SqlEndToEndTests(FunctionAppFixture fixture)
        {
            this._fixture = fixture;
        }

        [Fact]
        public async Task SqlInput_Output_Trigger_Succeeds()
        {
            var product = new Dictionary<string, object>()
            {
                { "ProductId", 1 },
                { "Name", "test" },
                { "Cost", 100 }
            };

            var productString = JsonConvert.SerializeObject(product);
            // Insert row into Products table using SqlOutput
            await Utilities.InvokeHttpTriggerPost("AddProduct", productString, HttpStatusCode.OK);

            // Read row from Products table using SqlInput
            await Utilities.InvokeHttpTrigger("GetProducts", "", HttpStatusCode.OK, productString);

            // Wait for the SqlTrigger to fire and insert a row into Product2 table
            Thread.Sleep(TimeSpan.FromSeconds(5));

            // ProductsTrigger function should have been triggered by the SqlTrigger and inserted a row into Product2 table
            await Utilities.InvokeHttpTrigger("GetProducts2", "", HttpStatusCode.OK, productString);
        }
    }
}
