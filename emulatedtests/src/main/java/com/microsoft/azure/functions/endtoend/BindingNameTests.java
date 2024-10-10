package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.Optional;

/**
 * Azure Functions with Timer trigger.
 */
public class BindingNameTests {
    /**
     * This function will be invoked with a http request and send back the url of the request back.
     */
    @FunctionName("BindingName")
    public HttpResponseMessage run(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET}, authLevel = AuthorizationLevel.ANONYMOUS, route = "BindingName/{testMessage}") HttpRequestMessage<Optional<String>> request,
            @BindingName("testMessage") String route,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");
        return request.createResponseBuilder(HttpStatus.OK).body(route).build();
    }

}
