package com.microsoft.azure.functions.samples;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.servicebus.ServiceBusMessageActions;

public class ServiceBusSdkFunctions {
    @FunctionName("ServiceBusSdkManualSettlement")
    public void receive(
            @ServiceBusQueueTrigger(name = "message", queueName = "sdk-messages",
                    connection = "ServiceBusConnection", autoCompleteMessages = false)
            ServiceBusReceivedMessage message,
            @BindingName("message") ServiceBusMessageActions actions,
            ExecutionContext context) {
        context.getLogger().info("Processing " + message.getMessageId() + ": " + message.getBody());
        actions.completeMessage(message);
    }

    @FunctionName("ServiceBusSdkBatch")
    public void receiveBatch(
            @ServiceBusQueueTrigger(name = "messages", queueName = "sdk-batches",
                    connection = "ServiceBusConnection", cardinality = Cardinality.MANY)
            ServiceBusReceivedMessage[] messages,
            ExecutionContext context) {
        for (ServiceBusReceivedMessage message : messages) {
            context.getLogger().info("Processing " + message.getMessageId() + ": " + message.getBody());
        }
    }
}
