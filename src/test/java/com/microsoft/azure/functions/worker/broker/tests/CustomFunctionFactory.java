package com.microsoft.azure.functions.worker.broker.tests;

public class CustomFunctionFactory {
    public Object newInstance(Class clz) {
        try {
            return clz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
