package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with HTTP trigger.
 */
public class HttpTriggerTests {
    /**
     * This function will listen at HTTP endpoint "/api/HttpTrigger".
     */
    @FunctionName("HttpTriggerJava")
    public HttpResponseMessage HttpTriggerJava(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }

    @FunctionName("HttpTriggerJavaThrows")
    public HttpResponseMessage HttpTriggerThrows(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context)  throws Exception{
        context.getLogger().info("Java HTTP trigger processed a request.");
        throw new Exception("Test Exception");
    }
}
