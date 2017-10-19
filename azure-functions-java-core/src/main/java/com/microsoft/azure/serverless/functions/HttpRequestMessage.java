package com.microsoft.azure.serverless.functions;

import java.net.URI;
import java.util.Map;

public interface HttpRequestMessage<T> {
    URI getUri();
    String getMethod();
    Map<String, String> getHeaders();
    Map<String, String> getQueryParameters();
    T getBody();

    <R> HttpResponseMessage<R> createResponse(int status, R body);
}
