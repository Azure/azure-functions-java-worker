package com.microsoft.azure.serverless.functions;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestMessage {
    private HttpRequestMessage() {
        this.headers = new HashMap<>();
        this.query = new HashMap<>();
    }

    public URI getUri() { return this.uri; }
    public String getMethod() { return this.method; }
    public Map<String, String> getHeaders() { return this.headers; }
    public Map<String, String> getQueryParameters() { return this.query; }
    public String getBody() { return this.body; }

    private String method;
    private URI uri;
    private Map<String, String> headers;
    private Map<String, String> query;
    private String body;

    public static class Builder {
        public Builder() { this.message = new HttpRequestMessage(); }
        public Builder setUri(URI uri) { this.message.uri = uri; return this; }
        public Builder setMethod(String method) { this.message.method = method; return this; }
        public Builder setBody(String body) { this.message.body = body; return this; }
        public Builder putAllHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.message.headers.putAll(headers);
            }
            return this;
        }
        public Builder putAllQueryParameters(Map<String, String> parameters) {
            if (parameters != null) {
                this.message.query.putAll(parameters);
            }
            return this;
        }

        public HttpRequestMessage build() {
            this.message.headers = Collections.unmodifiableMap(this.message.headers);
            return this.message;
        }
        private HttpRequestMessage message;
    }
}
