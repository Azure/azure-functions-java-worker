package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with Event Grid trigger.
 */
public class EventGridTriggerTests {
    /**
     * This function will be invoked when an event is received from Event Grid.
     */
    @FunctionName("EventGridTriggerJava")
    public void eventGridHandler(
        @EventGridTrigger(name = "eventgrid") String message, 
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Event Grid trigger function executed.");
        context.getLogger().info(message);
    }
}
