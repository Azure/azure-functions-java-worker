package com.microsoft.azure.webjobs.script.broker;

import java.net.*;
import java.util.*;

import com.google.gson.*;
import com.google.protobuf.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class TypeResolver {
    public static Object parseTypedData(TypedData data) {
        try {
            switch (data.getTypeVal()) {
                case String:
                    return data.getStringVal();
                case Json:
                    return new JsonParser().parse(data.getStringVal());
                case Bytes:
                    return data.getBytesVal();
                case Http:
                    return data.getHttpVal();
                default:
                    throw new UnsupportedOperationException();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("ParameterBinding data type \"" + data.getTypeVal() + "\" is not supported", e);
        }
    }

    public static TypedData toTypedData(Object data) {
        try {
            if (data instanceof String || data instanceof Integer || data instanceof Double) {
                return TypedData.newBuilder().setTypeVal(TypedData.Type.String).setStringVal(data.toString()).build();
            } else if (data instanceof HttpResponseMessage) {
                return TypedData.newBuilder().setTypeVal(TypedData.Type.Http).setHttpVal(toRpcHttp((HttpResponseMessage) data)).build();
            } else {
                return TypedData.newBuilder().setTypeVal(TypedData.Type.Json).setStringVal(new Gson().toJson(data)).build();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Return value type \"" + data.getClass() + "\" is not supported");
        }
    }

    private static HttpRequestMessage parseRpcHttp(RpcHttp rpc) {
        return new HttpRequestMessage.Builder().setMethod(rpc.getMethod())
                .setUri(URI.create(rpc.getUrl()))
                .setBody(rpc.getRawBody())
                .putAllHeaders(rpc.getHeadersMap())
                .putAllQueryParameters(rpc.getQueryMap())
                .build();
    }

    private static RpcHttp toRpcHttp(HttpResponseMessage response) {
        return RpcHttp.newBuilder().setStatusCode(((Integer) response.getStatus()).toString())
                .setBody(toTypedData(response.getBody()))
                .build();
    }

    public static Optional<Object> tryAssign(Class<?> targetType, Object data) {
        if (targetType.isAssignableFrom(data.getClass())) {
            return Optional.of(data);
        }
        return Optional.empty();
    }

    public static Optional<Object> tryConvert(Class<?> targetType, Object data) {
        Optional<Object> assignedValue = tryAssign(targetType, data);
        if (assignedValue.isPresent()) { return assignedValue; }
        try {
            if (data instanceof String) {
                if (targetType.equals(int.class) || targetType.equals(Integer.class)) {
                    return Optional.of(Integer.parseInt(data.toString()));
                } else if (targetType.equals(double.class) || targetType.equals(Double.class)) {
                    return Optional.of(Double.parseDouble(data.toString()));
                } else {
                    return Optional.of(new Gson().fromJson(data.toString(), targetType));
                }
            } else if (data instanceof RpcHttp) {
                if (targetType.equals(HttpRequestMessage.class)) {
                    return Optional.of(parseRpcHttp((RpcHttp) data));
                } else {
                    return tryConvert(targetType, parseTypedData(((RpcHttp) data).getBody()));
                }
            } else if (data instanceof ByteString) {
                if (targetType.equals(byte[].class) || targetType.equals(Byte[].class)) {
                    return Optional.of(((ByteString) data).toByteArray());
                }
            } else if (data instanceof JsonElement) {
                return Optional.of(new Gson().fromJson((JsonElement) data, targetType));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            Application.LOGGER.info("Unable to convert data from \"" + data.getClass() + "\" to \"" + targetType + "\"");
        }
        return Optional.empty();
    }
}
