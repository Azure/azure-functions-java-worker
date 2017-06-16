package com.microsoft.azure.serverless.functions;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    public HttpRequest(String method) {
        this.method = method;
        this.headers = new HashMap<>();
    }

    public String getMethod() { return this.method; }
    public HttpRequest setMethod(String method) { this.method = method; return this; }

    public URI getUri() { return this.uri; }
    public HttpRequest setUri(URI uri) { this.uri = uri; return this; }

    public Map<String, String> getHeaders() { return this.headers; }
    public HttpRequest putHeaders(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    private String method;
    private URI uri;
    private Map<String, String> headers;
}
