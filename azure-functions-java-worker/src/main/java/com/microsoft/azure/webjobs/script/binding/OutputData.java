package com.microsoft.azure.webjobs.script.binding;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class OutputData<T> extends BindingData<T> {
    OutputData(String name, T value) { super(name, new Value<>(value)); }

    ParameterBinding toParameterBinding() {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        this.buildTypedData(dataBuilder);
        return ParameterBinding.newBuilder().setName(this.getName()).setData(dataBuilder).build();
    }

    abstract void buildTypedData(TypedData.Builder data);
}

class NondeterministicOutputData extends OutputData<OutputParameter<?>> {
    NondeterministicOutputData(String name, OutputParameter<?> placeholder) { super(name, placeholder); }

    @Override
    void buildTypedData(TypedData.Builder data) {
        DeterministicOutputData<?> actualOutputData = RpcOutputData.parse(this.getName(), this.getActualValue().getValue());
        actualOutputData.buildTypedData(data);
    }
}

abstract class DeterministicOutputData<T> extends OutputData<T> {
    DeterministicOutputData(String name, T value) { super(name, value); }
}

class NullOutputData extends DeterministicOutputData<Object> {
    NullOutputData(String name) { super(name, null); }

    @Override
    void buildTypedData(TypedData.Builder data) {}
}
