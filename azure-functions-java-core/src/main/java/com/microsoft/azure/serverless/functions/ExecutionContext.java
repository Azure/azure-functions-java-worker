package com.microsoft.azure.serverless.functions;

import java.util.logging.Logger;

public interface ExecutionContext {
    Logger getLogger();
    String getInvocationId();
    HttpResponseMessage getResponse();
    HttpResponseMessage getResponse(String name);
}
