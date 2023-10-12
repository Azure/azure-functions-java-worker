using System;
using System.Collections.Generic;
using System.Net.Http.Headers;
using System.Net.Http;
using System.Threading.Tasks;
using E2ETestCases.Utils;
using Xunit;

namespace Azure.Functions.Java.Tests.E2E.Helpers.AppInsight
{
    public class AppInsightHelper
    {
        private const string QueryEndpoint = "https://api.applicationinsights.io/v1/apps/{0}/query?{1}";
        // Trace data will be triggered as soon as node/java app starts. 
        // Therefore, larger timespan is used here in case traces data will be tested last.
        // 15min is estimated on test suite max timeout plus some time buffer.
        private const string TracesParameter = "query=traces%7C%20where%20timestamp%20%20%3E%20ago(20min)%7C%20order%20by%20timestamp%20desc%20";

        public static bool ValidateData(QueryType queryType, String currentSdkVersion)
        {
            int loopCount = 1;
            List<QueryResultRow> data = QueryAzureMonitorTelemetry(queryType).Result;

            while (data == null || data.Count == 0) 
            {
                if (loopCount > 0)
                {
                    // Waiting for 60s for traces showing up in application insight portal
                    // TODO: is there a more elegant way to wait?
                    System.Threading.Thread.Sleep(60*1000);
                    loopCount--;
                }
                else {
                    throw new Exception("No Application Insights telemetry available");
                }
            }
            bool dataFound = false;
            foreach (QueryResultRow row in data)
            {
                if (row.sdkVersion.IndexOf(currentSdkVersion) >= 0)
                {
                    dataFound = true;
                    break;
                    // TODO: Add extra checks when test apps generate similar telemetry, validate SDK version only for now
                }
            }
            return dataFound;
        }

        public static async Task<List<QueryResultRow>> QueryAzureMonitorTelemetry(QueryType queryType, bool isInitialCheck = false)
        {
            List<QueryResultRow> aiResult;
            Func<List<QueryResultRow>, bool> retryCheck = (result) => result == null;
            string queryParemeter = "";
            switch (queryType)
            {
                //TODO: test other type of data as well, for now only test trace. 
                //case QueryType.exceptions:
                //    queryParemeter = ExceptionsParameter;
                //    break;
                //case QueryType.dependencies:
                //    queryParemeter = DependenciesParameter;
                //    break;
                //case QueryType.requests:
                //    queryParemeter = RequestParameter;
                //    break;
                case QueryType.traces:
                    queryParemeter = TracesParameter;
                    break;
                //case QueryType.statsbeat:
                //    queryParemeter = StatsbeatParameter;
                //    break;
            }

            aiResult = await QueryMonitorLogWithRestApi(queryType, queryParemeter);
            return aiResult;
        }

        private static async Task<List<QueryResultRow>> QueryMonitorLogWithRestApi(QueryType queryType, string parameterString)
        {
            try
            {
                HttpClient client = new HttpClient();
                client.DefaultRequestHeaders.Accept.Add(
                    new MediaTypeWithQualityHeaderValue("application/json"));

                var apiKeyVal = Constants.ApplicationInsightAPIKey;
                var appIdVal = Constants.ApplicationInsightAPPID;

                client.DefaultRequestHeaders.Add("x-api-key", apiKeyVal);
                var req = string.Format(QueryEndpoint, appIdVal, parameterString);
                var table = await GetHttpResponse(client, req);
                return GetJsonObjectFromQuery(table);
            }
            catch (Exception ex)
            {
                Console.WriteLine("Failed to query Azure Motinor Ex:" + ex.Message);
            }
            return null;
        }

        // Get http request response
        public static async Task<string> GetHttpResponse(HttpClient client, string url)
        {
            Uri uri = new Uri(url);
            HttpResponseMessage response = null;
            try
            {
                response = client.GetAsync(uri).Result;
            }
            catch (Exception e)
            {
                Console.WriteLine("GetXhrResponse Error " + e.Message); return null;
            }
            if (response == null)
            {
                Console.WriteLine("GetXhrResponse Error: No Response"); return null;
            }
            if (!response.IsSuccessStatusCode)
            {
                Console.WriteLine($"Response failed. Status code {response.StatusCode}");
            }
            return await response.Content.ReadAsStringAsync();
        }

        // Get latest query object with RestApiJsonSerializeRow format
        // prerequisite: query parameter is ordered by timestamp desc
        private static List<QueryResultRow> GetJsonObjectFromQuery(string table)
        {
            if (!(table?.Length > 0)) { return null; }
            RestApiJsonSerializeTable SerializeTable = Newtonsoft.Json.JsonConvert.DeserializeObject<RestApiJsonSerializeTable>(table);
            if (!(SerializeTable?.Tables?.Count > 0)) { return null; }
            List<QueryResultRow> kustoObjectList = new List<QueryResultRow>();
            List<RestApiJsonSerializeCols> columnObjects = SerializeTable.Tables[0].Columns;
            List<string[]> rows = SerializeTable.Tables[0].Rows;
            if (!(rows?.Count > 0)) { return null; }
            foreach (string[] row in rows)
            {
                QueryResultRow item = new QueryResultRow(columnObjects, row);
                kustoObjectList.Add(item);
            }
            return kustoObjectList;
        }
    }
}
