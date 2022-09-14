package com.microsoft.azure.functions.worker.binding.model;

import java.lang.reflect.Parameter;

public class ExecutionParameter {
    private final Parameter parameter;
    private Object payload;

    public ExecutionParameter(Parameter parameter, Object payload) {
        this.parameter = parameter;
        this.payload = payload;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }
}
