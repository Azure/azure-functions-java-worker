package com.microsoft.azure.serverless.functions;

public final class HttpResponseMessage {
    public HttpResponseMessage(int status) {
        this(status, null);
    }

    public HttpResponseMessage(Object body) {
        this(200, body);
    }

    public HttpResponseMessage(int status, Object body) {
        this.status = status;
        this.body = (body != null ? body : "");
    }

    public Integer getStatus() { return this.status; }
    public Object getBody() { return this.body; }

    private int status;
    private Object body;
}
