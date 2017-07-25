package com.microsoft.azure.webjobs.script.binding;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class OutputData extends BindingData<OutputParameter<?>> {
    OutputData(Object retValue) {
        super("$return", new Value<>(new OutputParameter<>(retValue)));
    }

    OutputData(String name, Class<?> target) throws InstantiationException, IllegalAccessException {
        super(name, new Value<>((OutputParameter<?>) target.newInstance()));
    }

    ParameterBinding toParameterBinding() {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        this.buildTypedData(dataBuilder);
        return ParameterBinding.newBuilder().setName(this.getName()).setData(dataBuilder).build();
    }

    abstract void buildTypedData(TypedData.Builder data);
}
