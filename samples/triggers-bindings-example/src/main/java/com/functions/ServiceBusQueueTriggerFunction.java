package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.*;

/**
 * Azure Functions with Azure Service Bus Queue.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-service-bus-trigger?tabs=java
 */
public class ServiceBusQueueTriggerFunction {

    @FunctionName("ServiceBusQueueTrigger")
    public void serviceBusQueueTrigger(
        @ServiceBusQueueTrigger(name = "message", queueName = "SBQueueNameSingle", connection = "AzureWebJobsServiceBus") String message,
        @QueueOutput(name = "output", queueName = "test-servicebusqueuesingle-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Service Bus Queue trigger function processed a message: " + message);
        output.setValue(message);
    }

    @FunctionName("ServiceBusQueueBatchTrigger")
    public void serviceBusQueueBatchTrigger(
        @ServiceBusQueueTrigger(name = "message", queueName = "SBQueueNameBatch", connection = "AzureWebJobsServiceBus", cardinality = Cardinality.MANY, dataType = "String") String[] messages,
        @QueueOutput(name = "output", queueName = "test-servicebusqueuebatch-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Service Bus Queue trigger function processed a message: " + messages[0]);
        output.setValue(messages[0]);
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
