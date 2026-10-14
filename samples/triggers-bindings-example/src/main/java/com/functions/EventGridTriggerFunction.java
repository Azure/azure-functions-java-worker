package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.*;

/**
 * Azure Functions with Event Grid trigger.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-event-grid-trigger?tabs=java%2Cbash
 */
public class EventGridTriggerFunction {
    /**
     * This function will be invoked when an event is received from Event Grid.
     */
    @FunctionName("EventGridTriggerJava")
    public void eventGridHandler(
        @EventGridTrigger(name = "eventgrid") String eventContent,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Grid trigger function executed.");
        context.getLogger().info(eventContent);
    }

    /**
     * This function will be invoked when a http trigger is received, and sends a custom event to Event Grid.
     */
    @FunctionName("EventGridOutputBindingJava")
    public HttpResponseMessage eventGridOutputBinding(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST},
            authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @EventGridOutput(name = "outputEvent",
            topicEndpointUri = "AzureWebJobsEventGridOutputBindingTopicUriString",
            topicKeySetting = "AzureWebJobsEventGridOutputBindingTopicKeyString") OutputBinding<EventGridEvent> outputEvent,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        String query = request.getQueryParameters().get("testuuid");
        String message = request.getBody().orElse(query);
        context.getLogger().info("testuuid:" + message);

        final EventGridEvent eventGridOutputDocument = new EventGridEvent();
        eventGridOutputDocument.setId("test-id");
        eventGridOutputDocument.setEventType("test-event-1");
        eventGridOutputDocument.setEventTime("2020-01-31T10:10:10+00:00");
        eventGridOutputDocument.setDataVersion("1.0");
        eventGridOutputDocument.setSubject("test-subject");
        eventGridOutputDocument.setData("test-uuid: " + message);

        outputEvent.setValue(eventGridOutputDocument);

        return request.createResponseBuilder(HttpStatus.OK).build();
    }
}


class EventGridEvent {
    private String id;
    private String eventType;
    private String subject;
    private String eventTime;
    private String dataVersion;
    private String data;

    public String getId() {
        return id;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getDataVersion() {
        return dataVersion;
    }

    public void setDataVersion(String dataVersion) {
        this.dataVersion = dataVersion;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setId(String id) {
        this.id = id;
    }
}
