// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

using System;
using System.Diagnostics;
using System.Threading.Tasks;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using Newtonsoft.Json.Linq;

namespace Azure.Functions.Java.Tests.E2E
{
    public static class Utilities
    {

        private static HttpClient httpClient = new HttpClient();

        public static async Task RetryAsync(Func<Task<bool>> condition, int timeout = 60 * 1000, int pollingInterval = 2 * 1000, bool throwWhenDebugging = false, Func<string> userMessageCallback = null)
        {
            DateTime start = DateTime.Now;
            while (!await condition())
            {
                await Task.Delay(pollingInterval);

                bool shouldThrow = !Debugger.IsAttached || (Debugger.IsAttached && throwWhenDebugging);
                if (shouldThrow && (DateTime.Now - start).TotalMilliseconds > timeout)
                {
                    string error = "Condition not reached within timeout.";
                    if (userMessageCallback != null)
                    {
                        error += " " + userMessageCallback();
                    }
                    throw new ApplicationException(error);
                }
            }
        }

        public static async Task<bool> InvokeHttpTrigger(string functionName, string queryString, HttpStatusCode expectedStatusCode, string expectedMessage, int expectedCode = 0)
        {
            string uri = $"{Constants.FunctionsHostUrl}/api/{functionName}{queryString}";
            using (var request = new HttpRequestMessage(HttpMethod.Get, uri))
            {
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/plain"));
                var response = await httpClient.SendAsync(request);

                Console.WriteLine(
                    $"InvokeHttpTrigger: {functionName}{queryString} : {response.StatusCode} : {response.ReasonPhrase}");
                if (expectedStatusCode != response.StatusCode && expectedCode != (int) response.StatusCode)
                {
                    return false;
                }

                if (!string.IsNullOrEmpty(expectedMessage))
                {
                    string actualMessage = await response.Content.ReadAsStringAsync();
                    Console.WriteLine(
                        $"InvokeHttpTrigger: expectedMessage : {expectedMessage}, actualMessage : {actualMessage}");
                    return actualMessage.Contains(expectedMessage);
                }

                return true;
            }
        }

        public static async Task<bool> InvokeEventGridTrigger(string functionName, JObject jsonContent, HttpStatusCode expectedStatusCode=HttpStatusCode.Accepted)
        {
            string uri = $"{Constants.FunctionsHostUrl}/runtime/webhooks/eventgrid?functionName={functionName}";
            using (HttpRequestMessage request = new HttpRequestMessage(HttpMethod.Get, uri))
            {
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));
                request.Headers.Add("aeg-event-type", "Notification");
                request.Content = new StringContent(
                    jsonContent.ToString(),
                    Encoding.UTF8,
                    "application/json"
                );

                var response = await httpClient.SendAsync(request);

                if (expectedStatusCode != response.StatusCode)
                {
                    Console.WriteLine(
                        $"InvokeEventGridTrigger: expectedStatusCode : {expectedStatusCode}, actualMessage : {response.StatusCode}");
                    return false;
                }

                return true;
            }
        }

        public static async Task<JObject> StartOrchestration(string functionName, HttpStatusCode expectedStatusCode)
        {
            string uri = $"{Constants.FunctionsHostUrl}/api/{functionName}";
            using (var request = new HttpRequestMessage(HttpMethod.Get, uri))
            {
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/plain"));
                var response = await httpClient.SendAsync(request);

                Console.WriteLine(
                    $"StartOrchestration: {functionName} : {response.StatusCode} : {response.ReasonPhrase}");
                if (expectedStatusCode != response.StatusCode)
                {
                    return null;
                }

                string output = await response.Content.ReadAsStringAsync();
                JObject jsonResponse = JObject.Parse(output);

                Console.WriteLine(
                    $"Started orchestration with instance ID: {jsonResponse["id"]}");
                return jsonResponse;
            }
        }

        public static async Task<JObject> InvokeUri(string uri)
        {
            using (var request = new HttpRequestMessage(HttpMethod.Get, uri))
            {
                request.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("text/plain"));
                var response = await httpClient.SendAsync(request);

                string output = await response.Content.ReadAsStringAsync();
                JObject jsonResponse = JObject.Parse(output);

                Console.WriteLine(
                    $"InvokeUrl response: {jsonResponse}");
                return jsonResponse;
            }
        }
    }
}
