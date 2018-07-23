package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.HttpRequestMessage;


public class Queue {

    public static void queueTrigger(
        @BindingName("myQueue") String message, 
        ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: "+message);
    }

    public static String queueOut(
        HttpRequestMessage<String> request, 
        ExecutionContext context
    ) {
        context.getLogger().info("Java Queue output function processed a message: "+request.getBody().toString());
        return request.getBody().toString();
    }
    
}