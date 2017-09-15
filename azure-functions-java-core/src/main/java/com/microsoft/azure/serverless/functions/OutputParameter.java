package com.microsoft.azure.serverless.functions;

public interface OutputParameter<T> {
    T getValue();
    void setValue(T value);
}
