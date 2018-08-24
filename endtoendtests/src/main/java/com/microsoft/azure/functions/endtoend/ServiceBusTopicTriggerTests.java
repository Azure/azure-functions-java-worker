package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with Azure Service Bus Topic.
 */
public class ServiceBusTopicTriggerTests {
    /**
     * This function will be invoked when a new Service Bus Topic message is received. The message contents are provided as input to this function.
     */
    @FunctionName("ServiceBusTopicTrigger")
    public void serviceBusTopicTrigger(
        @ServiceBusTopicTrigger(name = "message", topicName = "mysbtopic", subscriptionName="mysubs",connection = "AzureServiceBus") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Service Bus Topic trigger function processed a message: " + message);
    }

    /**
     * This function will be invoked when a http request is received. The message contents are provided as output to this function.
     */
    @FunctionName("ServiceBusTopicOutput")
    public void serviceBusTopicOutput(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @ServiceBusTopicOutput(name = "message", topicName = "mysbtopic", subscriptionName="mysubs",connection = "AzureServiceBus") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        String message = request.getBody().orElse("default message");
        output.setValue(message);
        context.getLogger().info("Java Service Bus Topic output function got a message: " + message);
    }
}
