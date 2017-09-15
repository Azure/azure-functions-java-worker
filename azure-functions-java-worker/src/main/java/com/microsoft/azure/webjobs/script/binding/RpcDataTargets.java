package com.microsoft.azure.webjobs.script.binding;

import com.google.gson.*;
import com.google.protobuf.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;
import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;

final class RpcHttpDataTarget extends DataTarget implements HttpResponseMessage {
    RpcHttpDataTarget() {
        super(HTTP_TARGET_OPERATIONS);
        this.status = 200;
        super.setValue(this);
    }

    @Override
    public void setValue(Object value) { throw new UnsupportedOperationException(); }
    @Override
    public int getStatus() { return this.status; }
    @Override
    public void setStatus(int status) { this.status = status; }
    @Override
    public Object getBody() { return this.body; }
    @Override
    public void setBody(Object body) { this.body = body; }

    private int status;
    private Object body;

    private static TypedData.Builder toHttpData(HttpResponseMessage response) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (response != null) {
            RpcHttp.Builder httpBuilder = RpcHttp.newBuilder().setStatusCode(response.getStatus() + "");
            RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
            bodyTarget.setValue(response.getBody());
            bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
            dataBuilder.setHttp(httpBuilder);
        }
        return dataBuilder;
    }

    private static final DataOperations<Object> HTTP_TARGET_OPERATIONS = new DataOperations<>();
    static {
        HTTP_TARGET_OPERATIONS.addOperation(TYPE_ASSIGNMENT, HttpResponseMessage.class, v -> toHttpData((HttpResponseMessage) v));
        HTTP_TARGET_OPERATIONS.addOperation(TYPE_ASSIGNMENT, RpcHttpDataTarget.class, v -> toHttpData((HttpResponseMessage) v));
    }
}

final class RpcUnspecifiedDataTarget extends DataTarget {
    RpcUnspecifiedDataTarget() { super(UNSPECIFIED_TARGET_OPERATIONS); }

    private static TypedData.Builder toStringData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            dataBuilder.setString(value.toString());
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
            }
        }
        return dataBuilder;
    }

    private static TypedData.Builder toByteArrayData(Object value) {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (value != null) {
            if (value instanceof byte[]) {
                dataBuilder.setBytes(ByteString.copyFrom((byte[]) value));
            }
        }
        return dataBuilder;
    }

    private static final DataOperations<Object> UNSPECIFIED_TARGET_OPERATIONS = new DataOperations<>();
    static {
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
                return new Gson().toJson(val);
            } catch (Exception ex) {
                return toStringData(val);
            }
        });
    }
}
