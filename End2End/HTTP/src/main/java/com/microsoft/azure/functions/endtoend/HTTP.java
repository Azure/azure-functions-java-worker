package com.microsoft.azure.functions.endtoend;

import java.util.*;
// import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

public class HTTP {

    public static HttpResponseMessage<String> httpTrigger(
        ExecutionContext context, 
        HttpRequestMessage<Optional<String>> request
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponse(400, "Please pass a name on the query string or in the request body");
        } else {
            return request.createResponse(200, "Hello, " + name);
        }
    }
}