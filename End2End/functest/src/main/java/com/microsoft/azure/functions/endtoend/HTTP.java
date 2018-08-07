package com.microsoft.azure.functions.endtoend;

import java.util.*;
import com.microsoft.azure.functions.*;

public class HTTP {

    public static HttpResponseMessage httpTrigger(
        HttpRequestMessage<Optional<String>> request,  
        ExecutionContext context        
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }
}