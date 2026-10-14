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
    public class MySqlEndToEndTests
    {
        private readonly FunctionAppFixture _fixture;

        public MySqlEndToEndTests(FunctionAppFixture fixture)
        {
            this._fixture = fixture;
        }

        [Fact]
        public async Task MySqlInput_Output_Succeeds()
        {
            TimeSpan t = DateTime.UtcNow - new DateTime(1970, 1, 1);
            int id  = (int) t.TotalSeconds;
            var product = new Dictionary<string, object>()
            {
                { "ProductId", id },
                { "Name", "test" },
                { "Cost", 100 }
            };

            var productString = JsonConvert.SerializeObject(product);
            // Insert a row into Products table using MySqlOutput
            Assert.True(await Utilities.InvokeHttpTriggerPost("AddProduct", productString, HttpStatusCode.OK));

            // Read row from Products table using MySqlInput
            Assert.True(await Utilities.InvokeHttpTrigger("GetProducts", "/" + id.ToString(), HttpStatusCode.OK, productString));
        }
    }
}