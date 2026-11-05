package com.microsoft.azure.samples;

import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.Cardinality;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.annotation.ServiceBusTopicTrigger;
import com.microsoft.azure.functions.servicebus.ServiceBusMessageActions;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service Bus triggers that take the SDK message type and settle it with ServiceBusMessageActions.
 * Every function logs one SBSDK line with the fields it decoded so the docker test can check them.
 */
public class ServiceBusSdkFunctions {

    static final String CONNECTION = "ServiceBusConnection";

    @FunctionName("CompleteMessage")
    public void complete(
        @ServiceBusQueueTrigger(name = "message", queueName = "sbdocker-complete", connection = CONNECTION)
        ServiceBusReceivedMessage message,
        ServiceBusMessageActions actions,
        final ExecutionContext context) {
        log(context, "CompleteMessage", message);
        actions.completeMessage(message);
        holdAfterSettlement(context, "CompleteMessage", message.getMessageId());
    }

    @FunctionName("DeadLetterMessage")
    public void deadLetter(
        @ServiceBusQueueTrigger(name = "message", queueName = "sbdocker-deadletter", connection = CONNECTION)
        ServiceBusReceivedMessage message,
        ServiceBusMessageActions actions,
        final ExecutionContext context) {
        log(context, "DeadLetterMessage", message);
        actions.deadLetterMessage(message, "sbdocker-reason", "sbdocker-description");
    }

    @FunctionName("DeferMessage")
    public void defer(
        @ServiceBusQueueTrigger(name = "message", queueName = "sbdocker-defer", connection = CONNECTION)
        ServiceBusReceivedMessage message,
        ServiceBusMessageActions actions,
        final ExecutionContext context) {
        log(context, "DeferMessage", message);
        actions.deferMessage(message);
    }

    @FunctionName("CompleteBatch")
    public void completeBatch(
        @ServiceBusQueueTrigger(name = "messages", queueName = "sbdocker-batch", connection = CONNECTION,
            cardinality = Cardinality.MANY)
        List<ServiceBusReceivedMessage> messages,
        ServiceBusMessageActions actions,
        final ExecutionContext context) {
        context.getLogger().info("SBSDK function=CompleteBatch batchSize=" + messages.size());
        for (ServiceBusReceivedMessage message : messages) {
            log(context, "CompleteBatch", message);
            actions.completeMessage(message);
        }
        holdAfterSettlement(context, "CompleteBatch",
            messages.stream().map(ServiceBusReceivedMessage::getMessageId).collect(Collectors.joining(",")));
    }

    @FunctionName("CompleteTopicMessage")
    public void completeTopic(
        @ServiceBusTopicTrigger(name = "message", topicName = "sbdocker-topic", subscriptionName = "sbdocker-sub",
            connection = CONNECTION)
        ServiceBusReceivedMessage message,
        ServiceBusMessageActions actions,
        final ExecutionContext context) {
        log(context, "CompleteTopicMessage", message);
        actions.completeMessage(message);
        holdAfterSettlement(context, "CompleteTopicMessage", message.getMessageId());
    }

    // Never settles. The host completes a message after a successful run unless the function settled it.
    @FunctionName("NoSettleMessage")
    public void noSettle(
        @ServiceBusQueueTrigger(name = "message", queueName = "sbdocker-nosettle", connection = CONNECTION)
        ServiceBusReceivedMessage message,
        final ExecutionContext context) {
        log(context, "NoSettleMessage", message);
    }

    // The host completes a message on its own after the function returns. Staying inside the function for a
    // while lets the test see that the broker already applied the settlement before that could happen.
    private static void holdAfterSettlement(ExecutionContext context, String function, String messageIds) {
        context.getLogger().info("SBSDK-SETTLED function=" + function + " messageIds=" + messageIds);
        try {
            Thread.sleep(15000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void log(ExecutionContext context, String function, ServiceBusReceivedMessage message) {
        Logger logger = context.getLogger();
        Map<String, Object> properties = new TreeMap<>(message.getApplicationProperties());
        logger.info("SBSDK function=" + function
            + " messageId=" + message.getMessageId()
            + " body=" + message.getBody().toString()
            + " lockToken=" + message.getLockToken()
            + " sequenceNumber=" + message.getSequenceNumber()
            + " deliveryCount=" + message.getDeliveryCount()
            + " subject=" + message.getSubject()
            + " contentType=" + message.getContentType()
            + " correlationId=" + message.getCorrelationId()
            + " properties=" + properties);
    }
}
