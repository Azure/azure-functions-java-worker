package com.microsoft.azure.functions.binding;

import java.lang.reflect.Type;
import java.util.*;

import com.google.protobuf.*;
import org.apache.commons.lang3.*;

import static com.microsoft.azure.functions.binding.BindingData.MatchingLevel.*;

final class RpcEmptyDataSource extends DataSource<Object> {
    RpcEmptyDataSource(String name) { super(name, null, EMPTY_DATA_OPERATIONS); }

    private static final DataOperations<Object, Object> EMPTY_DATA_OPERATIONS = new DataOperations<>();
    static {
        EMPTY_DATA_OPERATIONS.addOperation(TYPE_ASSIGNMENT, Optional.class, v -> Optional.empty());
        EMPTY_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, DataOperations::generalAssignment);
    }
}

final class RpcIntegerDataSource extends DataSource<Long> {
    RpcIntegerDataSource(String name, long value) { super(name, value, LONG_DATA_OPERATIONS); }

    private static final DataOperations<Long, Object> LONG_DATA_OPERATIONS = new DataOperations<>();
    static {
        LONG_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, int.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Integer.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, byte.class, Long::byteValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Byte.class, Long::byteValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_RELAXED_CONVERSION, String.class, Object::toString);
    }
}

final class RpcRealNumberDataSource extends DataSource<Double> {
    RpcRealNumberDataSource(String name, double value) { super(name, value, REALNUMBER_DATA_OPERATIONS); }

    private static final DataOperations<Double, Object> REALNUMBER_DATA_OPERATIONS = new DataOperations<>();
    static {
        REALNUMBER_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        REALNUMBER_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(TYPE_RELAXED_CONVERSION, String.class, Object::toString);
    }
}

final class RpcStringDataSource extends DataSource<String> {
    RpcStringDataSource(String name, String value) { super(name, value, STRING_DATA_OPERATIONS); }

    private static Object convertToJson(boolean isStrict, String s, Type target) {
        DataSource<?> jsonSource = new RpcJsonDataSource(null, s);
        if (isStrict) {
            return jsonSource.computeByType(TYPE_ASSIGNMENT, target).orElseThrow(ClassCastException::new).getNullSafeValue();
        } else {
            return jsonSource.computeByType(target).orElseThrow(ClassCastException::new).getNullSafeValue();
        }
    }

    private static final DataOperations<String, Object> STRING_DATA_OPERATIONS = new DataOperations<>();
    static {
        STRING_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, long.class, Long::parseLong);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Long.class, Long::parseLong);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, int.class, Integer::parseInt);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Integer.class, Integer::parseInt);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, short.class, Short::parseShort);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Short.class, Short::parseShort);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, byte.class, Byte::parseByte);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Byte.class, Byte::parseByte);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, double.class, Double::parseDouble);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Double.class, Double::parseDouble);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, float.class, Float::parseFloat);
        STRING_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Float.class, Float::parseFloat);
        STRING_DATA_OPERATIONS.addGuardOperation(TYPE_STRICT_CONVERSION, (v, t) -> convertToJson(true, v, t));
        STRING_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (v, t) -> convertToJson(false, v, t));
    }
}

final class RpcByteArrayDataSource extends DataSource<byte[]> {
    RpcByteArrayDataSource(String name, ByteString value) { super(name, value.toByteArray(), BYTE_ARRAY_DATA_OPERATIONS); }

    private static final DataOperations<byte[], Object> BYTE_ARRAY_DATA_OPERATIONS = new DataOperations<>();
    static {
        BYTE_ARRAY_DATA_OPERATIONS.addOperation(TYPE_ASSIGNMENT, Byte[].class, ArrayUtils::toObject);
        BYTE_ARRAY_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
}
