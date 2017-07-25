package com.microsoft.azure.serverless.functions;

public class OutputParameter<T> {
    public OutputParameter() {
    }

    public OutputParameter(T value) {
        this.value = value;
    }

    public T getValue() { return this.value; }
    public void setValue(T value) { this.value = value; }

    private T value;
}
