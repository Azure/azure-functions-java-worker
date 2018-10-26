package com.microsoft.azure.functions.worker.binding;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.protobuf.*;
import org.apache.commons.lang3.*;

final class RpcEmptyDataSource extends DataSource<Object> {
    RpcEmptyDataSource(String name) { super(name, null, EMPTY_DATA_OPERATIONS); }

    private static final DataOperations<Object, Object> EMPTY_DATA_OPERATIONS = new DataOperations<>();
    static {
        EMPTY_DATA_OPERATIONS.addOperation(Optional.class, v -> Optional.empty());
    }
}

final class RpcIntegerDataSource extends DataSource<Long> {
    RpcIntegerDataSource(String name, long value) { super(name, value, LONG_DATA_OPERATIONS); }

    private static final DataOperations<Long, Object> LONG_DATA_OPERATIONS = new DataOperations<>();
    static {        
        LONG_DATA_OPERATIONS.addOperation(int.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(Integer.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(Short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(byte.class, Long::byteValue);
        LONG_DATA_OPERATIONS.addOperation(Byte.class, Long::byteValue);
        LONG_DATA_OPERATIONS.addOperation(String.class, Object::toString);
    }
}

final class RpcRealNumberDataSource extends DataSource<Double> {
    RpcRealNumberDataSource(String name, double value) { super(name, value, REALNUMBER_DATA_OPERATIONS); }

    private static final DataOperations<Double, Object> REALNUMBER_DATA_OPERATIONS = new DataOperations<>();
    static {        
        REALNUMBER_DATA_OPERATIONS.addOperation(float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(Float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(String.class, Object::toString);
    }
}

final class RpcStringDataSource extends DataSource<String> {
    RpcStringDataSource(String name, String value) { super(name, value, STRING_DATA_OPERATIONS); }

    private static Object convertToJson(boolean isStrict, String s, Type target) throws JsonParseException, JsonMappingException, ClassCastException, IOException {
        DataSource<?> jsonSource = new RpcJsonDataSource(null, s);
        if (isStrict) {
            return jsonSource.computeByType(target).orElseThrow(ClassCastException::new).getNullSafeValue();
        } else {
            return jsonSource.computeByType(target).orElseThrow(ClassCastException::new).getNullSafeValue();
        }
    }

    private static final DataOperations<String, Object> STRING_DATA_OPERATIONS = new DataOperations<>();
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

final class RpcByteArrayDataSource extends DataSource<byte[]> {
    RpcByteArrayDataSource(String name, ByteString value) { super(name, value.toByteArray(), BYTE_ARRAY_DATA_OPERATIONS); }

    private static final DataOperations<byte[], Object> BYTE_ARRAY_DATA_OPERATIONS = new DataOperations<>();
    static {
        BYTE_ARRAY_DATA_OPERATIONS.addOperation(Byte[].class, ArrayUtils::toObject);        
    }
}
