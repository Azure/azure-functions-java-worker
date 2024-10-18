package com.microsoft.azure.functions.worker.binding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.protobuf.Timestamp;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.HttpStatusType;
import com.microsoft.azure.functions.rpc.messages.NullableTypes;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.RpcHttpCookie;
import com.microsoft.azure.functions.rpc.messages.TypedData;

final class RpcHttpDataTarget extends DataTarget implements HttpResponseMessage, HttpResponseMessage.Builder {
    RpcHttpDataTarget() {
        super(HTTP_TARGET_OPERATIONS);
        this.headers = new HashMap<>();
        this.cookies = new ArrayList<>();
        this.httpStatus = HttpStatus.OK;
        this.httpStatusCode = HttpStatus.OK.value();
        super.setValue(this);
    }
    
    @Override
    public HttpStatusType getStatus() {
        return httpStatus;
    }

    @Override
    public int getStatusCode() {
        return httpStatusCode;
    }

    @Override
    public String getHeader(String key) {
        return this.headers.get(key);
    }

    @Override
    public Object getBody() {
        return this.body;
    }

    private int httpStatusCode;
    private HttpStatusType httpStatus;
    private Object body;
    private Map<String, String> headers;
    private List<RpcHttpCookie> cookies;

    public static TypedData.Builder toRpcHttpData(RpcHttpDataTarget response) throws Exception {
        TypedData.Builder dataBuilder = TypedData.newBuilder();
        if (response != null) {
            RpcHttp.Builder httpBuilder = RpcHttp.newBuilder()
                .setStatusCode(Integer.toString(response.getStatusCode()));
            
            // Add headers
            response.headers.forEach(httpBuilder::putHeaders);

            // Add cookies
            if (response.cookies != null) {
                httpBuilder.addAllCookies(response.cookies);
            }

            RpcUnspecifiedDataTarget bodyTarget = new RpcUnspecifiedDataTarget();
            bodyTarget.setValue(response.getBody());
            bodyTarget.computeFromValue().ifPresent(httpBuilder::setBody);
            dataBuilder.setHttp(httpBuilder);
        }
        return dataBuilder;
    }

    private static final DataOperations<Object, TypedData.Builder> HTTP_TARGET_OPERATIONS = new DataOperations<>();
    static {
        HTTP_TARGET_OPERATIONS.addTargetOperation(HttpResponseMessage.class, v -> toRpcHttpData((RpcHttpDataTarget) v));
        HTTP_TARGET_OPERATIONS.addTargetOperation(RpcHttpDataTarget.class, v -> toRpcHttpData((RpcHttpDataTarget) v));
    }

    @Override
    public Builder status(HttpStatusType httpStatusType) {
        this.httpStatusCode = httpStatusType.value();
        this.httpStatus = httpStatusType;
        return this;
    }

    public Builder status(int httpStatusCode) {
        if (httpStatusCode < 100 || httpStatusCode > 599) {
            throw new IllegalArgumentException("Invalid HTTP Status code class. Valid classes are in the range of 1xx, 2xx, 3xx, 4xx and 5xx.");
        }
        this.httpStatusCode = httpStatusCode;
        this.httpStatus = HttpStatusType.custom(httpStatusCode);
        return this;
    }

    @Override
    public Builder header(String key, String value) {
        this.headers.put(key, value);
        return this;
    }

    public Builder cookie(HttpCookie cookie) {
        this.cookies.add(convertToRpcHttpCookie(cookie));
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

    private RpcHttpCookie convertToRpcHttpCookie(HttpCookie cookie) {
        RpcHttpCookie.Builder builder = RpcHttpCookie.newBuilder()
                .setName(cookie.getName())
                .setValue(cookie.getValue());

        if (cookie.getDomain() != null) {
            builder.setDomain(NullableTypes.NullableString.newBuilder().setValue(cookie.getDomain()).build());
        }
        if (cookie.getPath() != null) {
            builder.setPath(NullableTypes.NullableString.newBuilder().setValue(cookie.getPath()).build());
        }
        if (cookie.getExpires() != null) {
            try {
                // Parse the expires string into an Instant
                Instant instant = Instant.parse(cookie.getExpires());
                // Build the Timestamp from the Instant
                Timestamp expiresTimestamp = Timestamp.newBuilder()
                        .setSeconds(instant.getEpochSecond())
                        .setNanos(instant.getNano())
                        .build();
                builder.setExpires(NullableTypes.NullableTimestamp.newBuilder().setValue(expiresTimestamp).build());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid expires format in cookie", e);
            }
        }
        if (cookie.getMaxAge() != null) {
            builder.setMaxAge(NullableTypes.NullableDouble.newBuilder().setValue(cookie.getMaxAge()).build());
        }
        if (cookie.getSecure() != null) {
            builder.setSecure(NullableTypes.NullableBool.newBuilder().setValue(cookie.getSecure()).build());
        }
        if (cookie.getHttpOnly() != null) {
            builder.setHttpOnly(NullableTypes.NullableBool.newBuilder().setValue(cookie.getHttpOnly()).build());
        }
        if (cookie.getSameSite() != null) {
            builder.setSameSite(cookie.getSameSite());
        }
        return builder.build();
    }
}
