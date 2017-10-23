package com.microsoft.azure.serverless.functions;

import java.util.logging.Logger;

public interface ExecutionContext {
    Logger getLogger();
    String getInvocationId();
    String getFunctionName();
}
