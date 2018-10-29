package com.microsoft.azure.functions.worker.binding;

final class RpcRealNumberDataSource extends DataSource<Double> {
    RpcRealNumberDataSource(String name, double value) { super(name, value, REALNUMBER_DATA_OPERATIONS); }

    private static final DataOperations<Double, Object> REALNUMBER_DATA_OPERATIONS = new DataOperations<>();
    static {        
        REALNUMBER_DATA_OPERATIONS.addOperation(float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(Float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(String.class, Object::toString);
    }
}