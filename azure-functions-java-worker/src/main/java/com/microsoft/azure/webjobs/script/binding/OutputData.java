package com.microsoft.azure.webjobs.script.binding;

import com.microsoft.azure.webjobs.script.broker.TypeResolver;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class OutputData extends BindingData {
    public OutputData(Value value) {
        super("$return", value);
    }

    // public ParameterBinding toParameterBinding() {
        // return ParameterBinding.newBuilder().setName(this.getName()).setData(TypeResolver.toTypedData(this.value)).build();
    // }
}
