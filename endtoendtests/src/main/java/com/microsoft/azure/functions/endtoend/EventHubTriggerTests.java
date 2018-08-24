package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with Azure Event Hub.
 */
public class EventHubTriggerTests {
    /**
     * This function will be invoked when a new message is received at the specified EventHub. The message contents are provided as input to this function.
     */
    @FunctionName("EventHubTrigger")
    public void eventhubTrigger(
        @EventHubTrigger(name = "message", eventHubName = "myhub", connection = "AzureEventHub") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger function processed a message: " + message);
    }

    /**
     * This function will be invoked when a new http request is received at the http://localhost:7071/api/EventHubOutput. A new message will be added to the specified EventHub.
     */
    @FunctionName("EventHubOutput")
    public void eventHubOutput(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @EventHubOutput(name = "output", eventHubName = "myhub", connection = "AzureEventHub") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        String message = request.getBody().orElse("default message");
        context.getLogger().info("Java Event Hub Output function processed a message: " + message);
        output.setValue(message);
    }
}
