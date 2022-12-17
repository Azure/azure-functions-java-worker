package com.microsoft.azure.functions.worker.chain;

import java.lang.reflect.Parameter;

public class ExecutionParameter {
    private final Parameter parameter;
    private Object bindingData;

    public ExecutionParameter(Parameter parameter, Object bindingData) {
        this.parameter = parameter;
        this.bindingData = bindingData;
    }

    public Parameter getParameter() {
        return parameter;
    }

    public Object getBindingData() {
        return bindingData;
    }

    public void setBindingData(Object bindingData) {
        this.bindingData = bindingData;
    }
}
