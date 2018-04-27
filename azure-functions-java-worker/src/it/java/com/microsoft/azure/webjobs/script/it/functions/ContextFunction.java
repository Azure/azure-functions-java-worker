package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.ExecutionContext;

public class ContextFunction {
    public static String functionName(ExecutionContext context) {
        return context.getFunctionName();
    }

    public static String invocationId(ExecutionContext context) {
        return context.getInvocationId();
    }
}
