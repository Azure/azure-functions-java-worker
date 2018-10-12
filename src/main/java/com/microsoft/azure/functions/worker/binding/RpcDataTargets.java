package com.microsoft.azure.functions.worker.binding;

import java.util.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;

import org.apache.commons.lang3.exception.*;

import com.google.protobuf.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

import static com.microsoft.azure.functions.worker.binding.BindingData.MatchingLevel.*;

final class RpcHttpDataTarget extends DataTarget implements HttpResponseMessage, HttpResponseMessage.Builder {
    RpcHttpDataTarget() {
        super(HTTP_TARGET_OPERATIONS);
        this.headers = new HashMap<>();
        this.status = HttpStatus.valueOf(200);
        super.setValue(this);
    }

    @Override
    public HttpStatus getStatus() { return this.status; }
    @Override
    public String getHeader(String key) { return this.headers.get(key); }
    @Override
    public Object getBody() { return this.body; }

    private HttpStatus status;
    private Object body;
    private Map<String, String> headers;

    private static TypedData.Builder toHttpData(RpcHttpDataTarget response) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (response != null) {
            RpcHttp.Builder httpBuilder = RpcHttp.newBuilder().setStatusCode(response.getStatus().value() + "");
            response.headers.forEach(httpBuilder::putHeaders);
            RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
            bodyTarget.setValue(response.getBody());
            bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
            dataBuilder.setHttp(httpBuilder);
        }
        return dataBuilder;
    }

    private static final DataOperations<Object, TypedData.Builder> HTTP_TARGET_OPERATIONS = new DataOperations<>();
    static {
        HTTP_TARGET_OPERATIONS.addOperation(TYPE_ASSIGNMENT, HttpResponseMessage.class, v -> toHttpData((RpcHttpDataTarget) v));
        HTTP_TARGET_OPERATIONS.addOperation(TYPE_ASSIGNMENT, RpcHttpDataTarget.class, v -> toHttpData((RpcHttpDataTarget) v));
    }

	@Override
	public Builder status(HttpStatus status) {
        this.status = status;
		return this;
	}

	@Override
	public Builder header(String key, String value) {
        this.headers.put(key, value);
		return this;
	}

	@Override
	public Builder body(Object body) {
        this.body = body;
		return this;
	}

	@Override
	public HttpResponseMessage build() {
		return this;
	}
}

final class RpcUnspecifiedDataTarget extends DataTarget {
    RpcUnspecifiedDataTarget() { super(UNSPECIFIED_TARGET_OPERATIONS); }

    private static TypedData.Builder toStringData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            dataBuilder.setString(value.toString());
        } else {
            throw new ClassCastException("Cannot convert "+ value + "to String");
        }
        return dataBuilder;
    }

    private static TypedData.Builder toIntData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            if (value instanceof Long) {
                dataBuilder.setInt((Long) value);
            } else if (value instanceof Integer) {
                dataBuilder.setInt((Integer) value);
            } else if (value instanceof Short) {
                dataBuilder.setInt((Short) value);
            } else if (value instanceof Byte) {
                dataBuilder.setInt((Byte) value);
            } else {
                throw new ClassCastException("Cannot convert "+ value + "to Int");
            }
        }
        return dataBuilder;
    }

    private static TypedData.Builder toRealNumberData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            if (value instanceof Double) {
                dataBuilder.setDouble((Double) value);
            } else if (value instanceof Float) {
                dataBuilder.setDouble((Float) value);
            } else {
                throw new ClassCastException("Cannot convert "+ value + "to Double");
            }
        }
        return dataBuilder;
    }

    private static TypedData.Builder toByteArrayData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            if (value instanceof byte[]) {
                dataBuilder.setBytes(ByteString.copyFrom((byte[]) value));
            } else {
                throw new ClassCastException("Cannot convert "+ value + "to ByteString");
            }
        }
        return dataBuilder;
    }

    private static TypedData.Builder toJsonData(Object value) throws Exception {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            dataBuilder.setJson(RELAXED_JSON_MAPPER.writeValueAsString(value));
        } else {
            throw new IllegalArgumentException();
        }
        return dataBuilder;
    }

    private static final ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
    private static final DataOperations<Object, TypedData.Builder> UNSPECIFIED_TARGET_OPERATIONS = new DataOperations<>();
    static {
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_ASSIGNMENT, String.class, RpcUnspecifiedDataTarget::toStringData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, long.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Long.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, int.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Integer.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, short.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Short.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, byte.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Byte.class, RpcUnspecifiedDataTarget::toIntData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, double.class, RpcUnspecifiedDataTarget::toRealNumberData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Double.class, RpcUnspecifiedDataTarget::toRealNumberData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, float.class, RpcUnspecifiedDataTarget::toRealNumberData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, Float.class, RpcUnspecifiedDataTarget::toRealNumberData);
        UNSPECIFIED_TARGET_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, byte[].class, RpcUnspecifiedDataTarget::toByteArrayData);
        UNSPECIFIED_TARGET_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (val, type) -> {
            try {
                return toJsonData(val);
            } catch (Exception ex) {
                WorkerLogManager.getSystemLogger().warning(ExceptionUtils.getRootCauseMessage(ex));
                return toStringData(val);
            }
        });
    }
}
