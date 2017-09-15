package com.microsoft.azure.webjobs.script.binding;

import java.lang.reflect.Type;

import com.google.protobuf.*;

import com.microsoft.azure.webjobs.script.binding.BindingData.*;
import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;

final class RpcIntegerDataSource extends DataSource<Long> {
    RpcIntegerDataSource(String name, long value) { super(name, value, LONG_DATA_OPERATIONS); }

    private static final DataOperations<Long> LONG_DATA_OPERATIONS = new DataOperations<>();
    static {
        LONG_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, int.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Integer.class, Long::intValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Short.class, Long::shortValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, byte.class, Long::byteValue);
        LONG_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Byte.class, Long::byteValue);
    }
}

final class RpcRealNumberDataSource extends DataSource<Double> {
    RpcRealNumberDataSource(String name, double value) { super(name, value, REALNUMBER_DATA_OPERATIONS); }

    private static final DataOperations<Double> REALNUMBER_DATA_OPERATIONS = new DataOperations<>();
    static {
        REALNUMBER_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        REALNUMBER_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, float.class, Double::floatValue);
        REALNUMBER_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Float.class, Double::floatValue);
    }
}

final class RpcStringDataSource extends DataSource<String> {
    RpcStringDataSource(String name, String value) { super(name, value, STRING_DATA_OPERATIONS); }

    private static Object convertToJson(MatchingLevel level, String s, Type target) {
        return new RpcJsonDataSource(null, s).computeByType(level, target);
    }

    private static final DataOperations<String> STRING_DATA_OPERATIONS = new DataOperations<>();
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
        STRING_DATA_OPERATIONS.addGuardOperation(TYPE_STRICT_CONVERSION, (v, t) -> convertToJson(TYPE_STRICT_CONVERSION, v, t));
        STRING_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (v, t) -> convertToJson(TYPE_RELAXED_CONVERSION, v, t));
    }
}

final class RpcByteArrayDataSource extends DataSource<byte[]> {
    RpcByteArrayDataSource(String name, ByteString value) { super(name, value.toByteArray(), BYTE_ARRAY_DATA_OPERATIONS); }

    private static final DataOperations<byte[]> BYTE_ARRAY_DATA_OPERATIONS = new DataOperations<>();
    static {
        BYTE_ARRAY_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
}
