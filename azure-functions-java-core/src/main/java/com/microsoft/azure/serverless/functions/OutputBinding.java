package com.microsoft.azure.serverless.functions;

/**
 *
 * @since 1.0.0
 */
public interface OutputBinding<T> {
    T getValue();
    void setValue(T value);
}
