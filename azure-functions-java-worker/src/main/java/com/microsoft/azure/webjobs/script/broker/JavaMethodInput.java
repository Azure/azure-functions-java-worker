package com.microsoft.azure.webjobs.script.broker;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaMethodInput {
    public JavaMethodInput(ParameterBinding parameter) {
        this(parameter.getName(), parameter.getData());
    }

    public JavaMethodInput(Object value) {
        this(null, value);
    }

    public JavaMethodInput(String name, TypedData data) {
        this(name, TypeResolver.parseTypedData(data));
    }

    public JavaMethodInput(String name, Object value) {
        this.name = name;
        this.value = value;
    }

    public String getName() { return this.name; }
    public Object getValue() { return this.value; }

    private String name;
    private Object value;
}
