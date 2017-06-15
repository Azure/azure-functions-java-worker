package com.microsoft.azure.webjobs.script.broker;

import javax.annotation.*;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaMethodInput {
    public JavaMethodInput(ParameterBinding parameter) {
        System.out.println("-------------------------------------------------------");
        System.out.println("                    Input Data");
        System.out.println("-------------------------------------------------------");
        System.out.println("Name:      " + parameter.getName());
        System.out.println("Data Type: " + parameter.getData().getTypeVal());
        switch (parameter.getData().getTypeVal()) {
            case String:
                System.out.println("Data:      " + parameter.getData().getStringVal());
                break;
            case Bytes:
                System.out.println("Data:      " + parameter.getData().getBytesVal());
                break;
            case Http:
                System.out.println("Data:      " + parameter.getData().getHttpVal());
                break;
        }
        System.out.println("=======================================================");

        this.name = parameter.getName();
        setValueFromData(parameter.getData());
    }

    public String getSuggestedName() { return this.name; }
    public Object getValue() { return this.value; }

    @PostConstruct
    private void setValueFromData(TypedData data) {
        TypedData.Type type = data.getTypeVal();
        switch (type) {
            case String:
                this.value = data.getStringVal();
                break;
            default:
                throw new IllegalArgumentException("ParameterBinding data type \"" + type + "\" is not supported");
        }
    }

    private String name;
    private Object value;
}
