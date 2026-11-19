package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

public class StaticBlockFailure {
    static {
        Optional.empty().orElseThrow(() -> new RuntimeException("exception raised in static block"));
    }
    @FunctionName("StaticBlockFailure")
    public HttpResponseMessage run(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request of function StaticBlockFailure.");
        return request.createResponseBuilder(HttpStatus.OK).body("Hello, e2e test").build();
    }
}
