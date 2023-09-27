package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.converter.CoreTypeConverter;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

public final class ParamBindInfo {

    private final String name;
    private final Type type;
    private final String bindingNameAnnotation;
    private final boolean isImplicitOutput;
    private final Parameter parameter;
    ParamBindInfo(Parameter param) {
        this.name = CoreTypeConverter.getAnnotationName(param);
        this.type = param.getParameterizedType();
        this.bindingNameAnnotation = CoreTypeConverter.getBindingNameAnnotation(param);
        this.isImplicitOutput = CoreTypeConverter.checkImplicitOutput(param);
        this.parameter = param;
    }

    public boolean isImplicitOutput() {
        return isImplicitOutput;
    }

    public String getName() {
        return name;
    }

    public Type getType() {
        return type;
    }

    public String getBindingNameAnnotation() {
        return bindingNameAnnotation;
    }

    public Parameter getParameter() {
        return parameter;
    }
}
