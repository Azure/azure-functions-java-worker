package com.microsoft.azure.serverless.functions;

import java.util.logging.Logger;

public final class ExecutionContext {
    private ExecutionContext() {
    }

    public Logger getLogger() { return this.logger; }
    public String getInvocationId() { return this.invocationId; }

    private Logger logger;
    private String invocationId;

    public static class Builder {
        public Builder() {
            this.context = new ExecutionContext();
        }

        public ExecutionContext build() { return this.context; }

        public Builder setLogger(Logger logger) { this.context.logger = logger; return this; }
        public Builder setInvocationId(String id) { this.context.invocationId = id; return this; }

        private ExecutionContext context;
    }
}
