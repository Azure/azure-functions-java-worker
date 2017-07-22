package com.microsoft.azure.webjobs.script.binding;

import java.net.URI;

import com.google.gson.*;
import com.google.protobuf.*;

import com.microsoft.azure.serverless.functions.HttpRequestMessage;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class RpcInputData<T> extends InputData {
    RpcInputData(String name, T value) { super(name, new Value(value)); }

    @SuppressWarnings("unchecked")
    T getActualValue() { return (T)this.getValue().getActual(); }

    static RpcInputData parse(ParameterBinding parameter) {
        switch (parameter.getData().getDataCase()) {
            case STRING: return new RpcStringInputData(parameter.getName(), parameter.getData().getString());
            case JSON:   return new RpcJsonInputData(parameter.getName(), parameter.getData().getJson());
            case BYTES:  return new RpcBytesInputData(parameter.getName(), parameter.getData().getBytes());
            case HTTP:   return new RpcHttpInputData(parameter.getName(), parameter.getData().getHttp());
        }
        throw new UnsupportedOperationException("Input data type \"" + parameter.getData().getDataCase() + "\" is not supported");
    }

    private static class RpcStringInputData extends RpcInputData<String> {
        RpcStringInputData(String name, String value) { super(name, value); }
    }

    private static class RpcJsonInputData extends RpcInputData<JsonElement> {
        RpcJsonInputData(String name, String jsonString) { super(name, new JsonParser().parse(jsonString)); }
    }
}

class RpcHttpInputData extends RpcInputData<RpcHttp> {
    RpcHttpInputData(String name, RpcHttp value) {
        super(name, value);
        super.registerAssignment(HttpRequestMessage.class, this::toHttpRequestMessage);
    }

    private Value toHttpRequestMessage() {
        return new Value(new HttpRequestMessage.Builder()
            .setMethod(this.getActualValue().getMethod())
            .setUri(URI.create(this.getActualValue().getUrl()))
            .setBody(this.getActualValue().getBody().toString())
            .putAllHeaders(this.getActualValue().getHeadersMap())
            .putAllQueryParameters(this.getActualValue().getQueryMap())
            .build());
    }
}

class RpcBytesInputData extends RpcInputData<ByteString> {
    RpcBytesInputData(String name, ByteString value) {
        super(name, value);
        super.registerAssignment(byte[].class, () -> new Value(this.getActualValue().toByteArray()));
    }
}
