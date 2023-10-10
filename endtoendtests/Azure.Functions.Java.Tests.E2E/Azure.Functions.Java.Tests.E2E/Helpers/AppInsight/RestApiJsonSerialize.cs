using System.Collections.Generic;

namespace E2ETestCases.Utils
{
    public class RestApiJsonSerializeCols
    {
        public string Name { get; set; }
        public string Type { get; set; }
    }

    public class RestApiJsonSerializeObject
    {
        public string Name { get; set; }
        public List<RestApiJsonSerializeCols> Columns { get; set; }
        public List<string[]> Rows { get; set; }
    }

    public class RestApiJsonSerializeTable
    {
        public List<RestApiJsonSerializeObject> Tables { get; set; }
    }

    //TODO: add unit tests
    public class QueryResultRow
    {
        Dictionary<string, string> data = new Dictionary<string, string>();
        public QueryResultRow(List<RestApiJsonSerializeCols> cols, string[] rows)
        {
            for (int i = 0; i < rows.Length; i++)
            {
                data.Add(cols[i].Name, rows[i]);
            }
        }
        // Here are all fields might be used by Monitor log query results.
        // Only those fields in Dictionary(data) will be parsed.
        public string timestamp { get => getData("timestamp"); }
        public string id { get => getData("id"); }
        public string source { get => getData("source"); }
        public string name { get => getData("name"); }
        public string url { get => getData("url"); }
        public string target { get => getData("target"); }
        public string success { get => getData("success"); }
        public string resultCode { get => getData("resultCode"); }
        public string duration { get => getData("duration"); }
        public string performanceBucket { get => getData("performanceBucket"); }
        public string itemType { get => getData("itemType"); }
        public dynamic customDimensions { get => getData("customDimensions"); }
        public dynamic customMeasurements { get => getData("customMeasurements"); }
        public string operation_Name { get => getData("operation_Name"); }
        public string operation_Id { get => getData("operation_Id"); }
        public string operation_ParentId { get => getData("operation_ParentId"); }
        public string operation_SyntheticSource { get => getData("operation_SyntheticSource"); }
        public string session_Id { get => getData("session_Id"); }
        public string user_Id { get => getData("user_Id"); }
        public string user_AuthenticatedId { get => getData("user_AuthenticatedId"); }
        public string user_AccountId { get => getData("user_AccountId"); }
        public string application_Version { get => getData("application_Version"); }
        public string client_Type { get => getData("client_Type"); }
        public string client_Model { get => getData("client_Model"); }
        public string client_OS { get => getData("client_OS"); }
        public string client_IP { get => getData("client_IP"); }
        public string client_City { get => getData("client_City"); }
        public string client_StateOrProvince { get => getData("client_StateOrProvince"); }
        public string client_CountryOrRegion { get => getData("client_CountryOrRegion"); }
        public string client_Browser { get => getData("client_Browser"); }
        public string cloud_RoleName { get => getData("cloud_RoleName"); }
        public string cloud_RoleInstance { get => getData("cloud_RoleInstance"); }
        public string appId { get => getData("appId"); }
        public string appName { get => getData("appName"); }
        public string iKey { get => getData("iKey"); }
        public string sdkVersion { get => getData("sdkVersion"); }
        public string itemId { get => getData("itemId"); }
        public string itemCount { get => getData("itemCount"); }
        public string _ResourceId { get => getData("_ResourceId"); }
        public string problemId { get => getData("problemId"); }
        public string handledAt { get => getData(" handledAt"); }
        public string type { get => getData(" type"); }
        public string message { get => getData("message"); }
        public string assembly { get => getData("assembly"); }
        public string method { get => getData("method"); }
        public string outerType { get => getData("outerType"); }
        public string outerMessage { get => getData("outerMessage"); }
        public string outerAssembly { get => getData("outerAssembly"); }
        public string outerMethod { get => getData("outerMethod"); }
        public string innermostType { get => getData("innermostType"); }
        public string innermostMessage { get => getData("innermostMessage"); }
        public string innermostAssembly { get => getData("innermostAssembly"); }
        public string innermostMethod { get => getData("innermostMethod"); }
        public string severityLevel { get => getData("severityLevel"); }
        public string details { get => getData("details"); }

        private string getData(string name)
        {
            return data.ContainsKey(name) ? data[name] : null;
        }
    }
}


