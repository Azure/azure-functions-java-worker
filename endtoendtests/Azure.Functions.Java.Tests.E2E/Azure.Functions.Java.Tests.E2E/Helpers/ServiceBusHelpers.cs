// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Microsoft.Azure.ServiceBus;
using Newtonsoft.Json;
using Newtonsoft.Json.Linq;
using System.Collections.Generic;
using System.Text;
using System.Threading.Tasks;

namespace Azure.Functions.Java.Tests.E2E
{
    public class ServiceBusHelpers
    {
        public static async Task SendMessagesAsync(string eventId, string serviceBusName, string connectionString)
        {
            byte[] messageBody = System.Text.Encoding.ASCII.GetBytes(eventId);
            ServiceBusConnectionStringBuilder builder = new ServiceBusConnectionStringBuilder(connectionString);
            builder.EntityPath = serviceBusName;
            QueueClient client = new QueueClient(builder, ReceiveMode.PeekLock);
            await client.SendAsync(new Message(messageBody));
        }
    }
}
