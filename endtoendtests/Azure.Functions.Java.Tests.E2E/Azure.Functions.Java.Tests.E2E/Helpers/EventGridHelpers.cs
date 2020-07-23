// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using Newtonsoft.Json.Linq;

using System;
using System.Collections.Generic;
using System.Threading.Tasks;

using Microsoft.Azure.EventGrid;
using Microsoft.Azure.EventGrid.Models;

namespace Azure.Functions.Java.Tests.E2E.Helpers
{
    public class EventGridHelpers
    {
        private static EventGridClient _eventGridClient;

        private static string topicHostname = null;

        public EventGridHelpers(string eventGridTopicEndpoint, string eventGridTopicKey)
        {
            TopicCredentials topicCredentials = new TopicCredentials(eventGridTopicKey);
            _eventGridClient = new EventGridClient(topicCredentials);

            topicHostname = new Uri(eventGridTopicEndpoint).Host;
        }

        public async Task SendEventAsync(string eventId, string subject,
            JObject data, string eventType, DateTime eventDateTime, string dataVersion)
        {
            List<EventGridEvent> events = new List<EventGridEvent>();
            var eventGridEvent = new EventGridEvent(eventId, subject, data,
                eventType, eventDateTime, dataVersion);
            events.Add(eventGridEvent);

            await _eventGridClient.PublishEventsAsync(topicHostname, events);
        }

        public static async Task SendEventsAsync(List<EventGridEvent> eventGridEvents)
        {
            await _eventGridClient.PublishEventsAsync(topicHostname, eventGridEvents);
        }
    }
}
