package com.microsoft.azure.webjobs.script.binding;

import java.net.*;
import java.util.*;

import com.google.gson.*;
import com.google.protobuf.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class RpcInputData<T> extends InputData<T> {
    RpcInputData(String name, T value) { super(name, new Value<>(value)); }

    static RpcInputData<?> parse(ParameterBinding parameter) { return parse(parameter.getName(), parameter.getData()); }
    static RpcInputData<?> parse(TypedData data) { return parse(null, data); }
    private static RpcInputData<?> parse(String name, TypedData data) {
        switch (data.getDataCase()) {
            case INT:    return new RpcIntegerInputData(name, data.getInt());
            case DOUBLE: return new RpcRealNumberInputData(name, data.getDouble());
            case STRING: return new RpcStringInputData(name, data.getString());
            case JSON:   return new RpcPojoInputData(name, data.getJson());
            case BYTES:  return new RpcBytesInputData(name, data.getBytes());
            case HTTP:   return new RpcHttpInputData(name, data.getHttp());
        }
        throw new UnsupportedOperationException("Input data type \"" + data.getDataCase() + "\" is not supported");
    }
}

class RpcIntegerInputData extends RpcInputData<Long> {
    RpcIntegerInputData(String name, Long value) {
        super(name, value);
        this.registerAssignment(int.class, () -> this.getActualValue().intValue());
        this.registerAssignment(short.class, () -> this.getActualValue().shortValue());
        this.registerAssignment(byte.class, () -> this.getActualValue().byteValue());
    }
}

class RpcRealNumberInputData extends RpcInputData<Double> {
    RpcRealNumberInputData(String name, Double value) {
        super(name, value);
        this.registerAssignment(float.class, () -> this.getActualValue().floatValue());
    }
}

class RpcStringInputData extends RpcInputData<String> {
    RpcStringInputData(String name, String value) {
        super(name, value);
        this.setOrElseConversion(target -> Optional.of(new Value<>(new Gson().fromJson(this.getActualValue(), target))));
    }
}

class RpcPojoInputData extends RpcInputData<JsonElement> {
    RpcPojoInputData(String name, String jsonString) {
        super(name, new JsonParser().parse(jsonString));
        this.setOrElseAssignment(target -> Optional.of(new Value<>(new Gson().fromJson(this.getActualValue(), target))));
    }
}

class RpcBytesInputData extends RpcInputData<ByteString> {
    RpcBytesInputData(String name, ByteString value) {
        super(name, value);
        this.registerAssignment(byte[].class, () -> this.getActualValue().toByteArray());
    }
}

class RpcHttpInputData extends RpcInputData<RpcHttp> implements HttpRequestMessage {
    RpcHttpInputData(String name, RpcHttp value) {
        super(name, value);
        this.registerAssignment(HttpRequestMessage.class, this::toHttpRequestMessage);
        this.body = (value.hasBody() ? parse(value.getBody()) : new NullInputData());
        this.setOrElseConversion(target -> this.body.convertTo(target));
        this.fieldMaps.add(value.getHeadersMap());
        this.fieldMaps.add(value.getQueryMap());
        this.fieldMaps.add(value.getParamsMap());
    }

    @Override
    Optional<InputData<?>> lookupSingleChildByName(String name) {
        return Utility.single(this.fieldMaps, map -> {
            String value = map.get(name);
            return (value != null ? Optional.of(new RpcStringInputData(name, value)) : Optional.empty());
        });
    }

    private HttpRequestMessage toHttpRequestMessage() {
        this.uri = URI.create(this.getActualValue().getUrl());
        return this;
    }

    @Override
    public URI getUri() { return this.uri; }
    @Override
    public String getMethod() { return this.getActualValue().getMethod(); }
    @Override
    public Map<String, String> getHeaders() { return this.getActualValue().getHeadersMap(); }
    @Override
    public Map<String, String> getQueryParameters() { return this.getActualValue().getQueryMap(); }
    @Override
    public Object getBody() {
        if (this.body instanceof RpcBytesInputData) {
            return ((RpcBytesInputData)this.body).getActualValue().toByteArray();
        } else if (this.body instanceof RpcPojoInputData) {
            return ((RpcPojoInputData)this.body).getActualValue().getAsString();
        } else if (this.body instanceof RpcHttpInputData) {
            return ((RpcHttpInputData)this.body).getBody();
        }
        return this.body.getActualValue();
    }

    private URI uri;
    private InputData<?> body;
    private List<Map<String, String>> fieldMaps = new ArrayList<>();
}
