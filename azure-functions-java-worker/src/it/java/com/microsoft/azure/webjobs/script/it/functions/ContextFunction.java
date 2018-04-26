package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.ExecutionContext;

public class ContextFunction {
    public static String testFunctionName(ExecutionContext context) {
        return context.getFunctionName();
    }
}
