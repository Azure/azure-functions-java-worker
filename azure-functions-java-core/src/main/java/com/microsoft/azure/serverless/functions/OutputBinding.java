package com.microsoft.azure.serverless.functions;

public interface OutputBinding<T> {
    T getValue();
    void setValue(T value);
}
