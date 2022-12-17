package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Type;

public final class RpcStringDataSource extends DataSource<String> {
    public RpcStringDataSource(String name, String value) { super(name, value, STRING_DATA_OPERATIONS); }

    private static Object convertToJson(boolean isStrict, String s, Type target) throws ClassCastException {
        DataSource<?> jsonSource = new RpcJsonDataSource(null, s);
        return jsonSource.computeByType(target).orElseThrow(ClassCastException::new).getNullSafeValue();
    }

    static final DataOperations<String, Object> STRING_DATA_OPERATIONS = new DataOperations<>();
    static {
        STRING_DATA_OPERATIONS.addOperation(long.class, Long::parseLong);
        STRING_DATA_OPERATIONS.addOperation(Long.class, Long::parseLong);
        STRING_DATA_OPERATIONS.addOperation(int.class, Integer::parseInt);
        STRING_DATA_OPERATIONS.addOperation(Integer.class, Integer::parseInt);
        STRING_DATA_OPERATIONS.addOperation(short.class, Short::parseShort);
        STRING_DATA_OPERATIONS.addOperation(Short.class, Short::parseShort);
        STRING_DATA_OPERATIONS.addOperation(byte.class, Byte::parseByte);
        STRING_DATA_OPERATIONS.addOperation(Byte.class, Byte::parseByte);
        STRING_DATA_OPERATIONS.addOperation(double.class, Double::parseDouble);
        STRING_DATA_OPERATIONS.addOperation(Double.class, Double::parseDouble);
        STRING_DATA_OPERATIONS.addOperation(float.class, Float::parseFloat);
        STRING_DATA_OPERATIONS.addOperation(Float.class, Float::parseFloat);        
        STRING_DATA_OPERATIONS.addGenericOperation(String.class, (v, t) -> convertToJson(false, v, t));
    }
}