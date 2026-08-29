package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.*;

/**
 * Azure Functions with Azure Event Hub.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-event-hubs-trigger?tabs=java
 */
public class EventHubTriggerFunction {
    /**
     * This function will be invoked when a new message is received at the specified EventHub. The message contents are provided as input to this function.
     */
    @FunctionName("EventHubTriggerAndOutputJSON")
    public void EventHubTriggerAndOutputJSON(
        @EventHubTrigger(name = "messages", eventHubName = "test-inputjson-java", connection = "AzureWebJobsEventHubSender", cardinality = Cardinality.MANY) List<String> messages,
        @EventHubOutput(name = "output", eventHubName = "test-outputjson-java", connection = "AzureWebJobsEventHubSender") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received " + messages.size() + " messages");
        output.setValue(messages.get(0));
    }

    @FunctionName("EventHubTriggerAndOutputString")
    public void EventHubTriggerAndOutputString(
        @EventHubTrigger(name = "messages", eventHubName = "test-input-java", connection = "AzureWebJobsEventHubSender", dataType = "string", cardinality = Cardinality.MANY) String[] messages,
        @BindingName("SystemPropertiesArray") SystemProperty[] systemPropertiesArray,
        @EventHubOutput(name = "output", eventHubName = "test-output-java", connection = "AzureWebJobsEventHubSender") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received " + messages.length + " messages");
        context.getLogger().info("SystemProperties for message[0]: EnqueuedTimeUtc=" + systemPropertiesArray[0].EnqueuedTimeUtc + " Offset=" + systemPropertiesArray[0].Offset);
        output.setValue(messages[0]);

    }

    @FunctionName("EventHubTriggerCardinalityOne")
    public void EventHubTriggerCardinalityOne(
        @EventHubTrigger(name = "message", eventHubName = "test-inputOne-java", connection = "AzureWebJobsEventHubSender", dataType = "string", cardinality = Cardinality.ONE) String message,
        @EventHubOutput(name = "output", eventHubName = "test-outputone-java", connection = "AzureWebJobsEventHubSender") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received message" + message);
        output.setValue(message);
    }

    /**
     * This function verifies the above functions
     */
    @FunctionName("EventHubOutputJson")
    public void TestEventHubOutputJson(
        @EventHubTrigger(name = "message", eventHubName = "test-outputjson-java", connection = "AzureWebJobsEventHubSender") String message,
        @QueueOutput(name = "output", queueName = "test-eventhuboutputjson-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub Output function processed a message: " + message);
        output.setValue(message);
    }

    @FunctionName("EventHubOutput")
    public void TestEventHubOutput(
        @EventHubTrigger(name = "message", eventHubName = "test-output-java", connection = "AzureWebJobsEventHubSender", cardinality = Cardinality.ONE) String message,
        @QueueOutput(name = "output", queueName = "test-eventhuboutput-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub Output function processed a message: " + message);
        output.setValue(message);
    }

    @FunctionName("EventHubOutputInputOne")
    public void TestEventHubOutputInputOne(
        @EventHubTrigger(name = "message", eventHubName = "test-outputone-java", connection = "AzureWebJobsEventHubSender", cardinality = Cardinality.ONE) String message,
        @QueueOutput(name = "output", queueName = "test-eventhuboutputone-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub Output function processed a message: " + message);
        output.setValue(message);
    }

    @FunctionName("EventHubTriggerAndOutputBinaryCardinalityManyListBinary")
    public void EventHubTriggerAndOutputBinaryCardinalityManyListBinary(
        @EventHubTrigger(name = "messages", eventHubName = "test-binary-input-cardinality-many-list-java", connection = "AzureWebJobsEventHubSender_2", dataType = "binary", cardinality = Cardinality.MANY) List<byte[]> messages,
        @QueueOutput(name = "output", queueName = "test-binary-output-cardinality-many-list-java", connection = "AzureWebJobsStorage") OutputBinding<byte[]> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received " + messages.size() + " messages");
        output.setValue(messages.get(0));
    }

    @FunctionName("EventHubTriggerAndOutputBinaryCardinalityOne")
    public void EventHubTriggerAndOutputBinaryCardinalityOne(
        @EventHubTrigger(name = "message", eventHubName = "test-binary-input-cardinality-one-java", connection = "AzureWebJobsEventHubSender_2", dataType = "binary", cardinality = Cardinality.ONE) byte[] message,
        @QueueOutput(name = "output", queueName = "test-binary-output-cardinality-one-java", connection = "AzureWebJobsStorage") OutputBinding<byte[]> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received message" + message);
        output.setValue(message);
    }

    @FunctionName("EventHubTriggerAndOutputBinaryCardinalityManyArrayBinary")
    public void EventHubTriggerAndOutputBinaryCardinalityManyArrayBinary(
        @EventHubTrigger(name = "messages", eventHubName = "test-binary-input-cardinality-many-array-java", connection = "AzureWebJobsEventHubSender_2", dataType = "binary", cardinality = Cardinality.MANY) byte[][] messages,
        @QueueOutput(name = "output", queueName = "test-binary-output-cardinality-many-array-java", connection = "AzureWebJobsStorage") OutputBinding<byte[]> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Hub trigger received " + messages.length + " messages");
        output.setValue(messages[0]);
    }

    public static class SystemProperty {
        public String SequenceNumber;
        public String Offset;
        public String PartitionKey;
        public String EnqueuedTimeUtc;

        public SystemProperty(String sequenceNumber, String offset, String partitionKey, String enqueuedTimeUtc) {
            this.SequenceNumber = sequenceNumber;
            this.Offset = offset;
            this.PartitionKey = partitionKey;
            this.EnqueuedTimeUtc = enqueuedTimeUtc;
        }
    }
}
