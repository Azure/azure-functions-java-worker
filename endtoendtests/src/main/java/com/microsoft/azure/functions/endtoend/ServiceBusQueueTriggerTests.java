package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with Azure Service Bus Queue.
 */
public class ServiceBusQueueTriggerTests {
    /**
     * This function will be invoked when a new message is received. The message contents are provided as input to this function.
     */
    @FunctionName("ServiceBusQueueTrigger")
    public void serviceBusQueueTrigger(
        @ServiceBusQueueTrigger(name = "message", queueName = "%SBQueueName%", connection = "AzureWebJobsServiceBus") String message,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Service Bus Queue trigger function processed a message: " + message);
    }

    /**
     * This function will be invoked when a http request is received. The message contents are provided as output to this function.
     */
    @FunctionName("ServiceBusQueueOutput")
    public void serviceBusQueueOutput(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @ServiceBusQueueOutput(name = "output", queueName = "%SBQueueName%", connection = "AzureWebJobsServiceBus") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        String message = request.getBody().orElse("default message");
        output.setValue(message);
        context.getLogger().info("Java Service Bugs Queue output function got a message: " + message);
    }

  
}
