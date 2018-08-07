package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.ExecutionContext;

public class Timer {

    public static void timerTrigger(
        ExecutionContext context
    ) {
        context.getLogger().info("Java Timer trigger function executed!");
    }    
}