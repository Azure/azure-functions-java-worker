package com.microsoft.azure.functions.worker.broker.tests;

public class BadMethodFunctionFactory {
    public Object create(Class clz) {
        try {
            return clz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
