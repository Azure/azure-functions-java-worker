package com.microsoft.azure.serverless.functions;

import java.net.URI;
import java.util.Map;

public interface HttpRequestMessage {
    URI getUri();
    String getMethod();
    Map<String, String> getHeaders();
    Map<String, String> getQueryParameters();
    Object getBody();

    HttpResponseMessage createResponse();
}
