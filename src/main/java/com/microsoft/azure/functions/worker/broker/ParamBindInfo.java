package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

public final class ParamBindInfo {
    private final String name;
    private final Type type;
    private String bindingNameAnnotation;
    private final boolean hasImplicitOutput;

    private final Parameter parameter;
    public ParamBindInfo(Parameter param) {
        this.name = CoreTypeResolver.getAnnotationName(param);
        this.type = param.getParameterizedType();
        this.bindingNameAnnotation = CoreTypeResolver.getBindingNameAnnotation(param);
        this.hasImplicitOutput = CoreTypeResolver.checkHasImplicitOutput(param);
        this.parameter = param;
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

    public boolean isHasImplicitOutput() {
        return hasImplicitOutput;
    }

    public Parameter getParameter() {
        return parameter;
    }
}
