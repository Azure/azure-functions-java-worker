package com.microsoft.azure.serverless.functions;

public interface HttpResponseMessage {
    int getStatus();
    void setStatus(int status);
    Object getBody();
    void setBody(Object body);
}
