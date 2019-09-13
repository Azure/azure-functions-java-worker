package com.microsoft.azure.functions.worker.binding;

import java.util.Map;

import com.microsoft.azure.functions.TraceContext;

final public class ExecutionTraceContext implements TraceContext {
    public ExecutionTraceContext(String traceParent, String traceState,  Map<String, String> attributes) {
        this.Attributes = attributes;
        this.Traceparent = traceParent;
        this.Tracestate = traceState;
    }

    @Override
    public String getTraceparent() { return this.Traceparent; }

    @Override
    public String getTracestate() { return this.Tracestate; }

    @Override
    public Map<String, String> getAttributes() { return this.Attributes; }

    private final String Traceparent;
    private final String Tracestate;
    private final Map<String, String> Attributes;
}