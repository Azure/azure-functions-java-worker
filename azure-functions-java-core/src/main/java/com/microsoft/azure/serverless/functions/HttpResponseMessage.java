package com.microsoft.azure.serverless.functions;

public interface HttpResponseMessage<T> {
    int getStatus();
    void setStatus(int status);
    Object getBody();
    void setBody(T body);
}
