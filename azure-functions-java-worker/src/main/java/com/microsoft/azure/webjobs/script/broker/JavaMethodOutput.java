package com.microsoft.azure.webjobs.script.broker;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaMethodOutput {
    public JavaMethodOutput(Object value) {
        System.out.println("-------------------------------------------------------");
        System.out.println("                    Output Data");
        System.out.println("-------------------------------------------------------");
        System.out.println("Name:      " + "$return");
        System.out.println("Data Type: " + value.getClass());
        System.out.println("Data:      " + TypeResolver.toTypedData(value).toString());
        System.out.println("=======================================================");

        this.value = value;
    }

    public ParameterBinding toParameterBinding() {
        return ParameterBinding.newBuilder().setName(this.getName()).setData(TypeResolver.toTypedData(this.value)).build();
    }

    private String getName() {
        return "$return";
    }

    private Object value;
}
