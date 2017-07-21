package com.microsoft.azure.serverless.functions;

public class OutputParameter<T> {
    public OutputParameter() {
    }

    public T getValue() { return this.value; }
    public void setValue(T value) { this.value = value; }

    private T value;
}
