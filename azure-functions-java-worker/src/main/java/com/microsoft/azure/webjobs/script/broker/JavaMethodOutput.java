package com.microsoft.azure.webjobs.script.broker;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaMethodOutput {
    public JavaMethodOutput(Object value) {
        System.out.println("-------------------------------------------------------");
        System.out.println("                    Output Data");
        System.out.println("-------------------------------------------------------");
        System.out.println("Name:      " + "$return");
        System.out.println("Data Type: " + value.getClass());
        System.out.println("Data:      " + value.toString());
        System.out.println("=======================================================");

        this.value = value;
    }

    public ParameterBinding.Builder toParameterBinding() {
        return ParameterBinding.newBuilder().setName(this.getName()).setData(this.getData());
    }

    private String getName() {
        return "$return";
    }

    private TypedData.Builder getData() {
        TypedData.Builder builder = TypedData.newBuilder();
        if (value instanceof String) {
            builder.setTypeVal(TypedData.Type.String).setStringVal(value.toString());
        } else {
            throw new IllegalArgumentException("Return value type \"" + value.getClass() + "\" is not supported");
        }
        return builder;
    }

    private Object value;
}
