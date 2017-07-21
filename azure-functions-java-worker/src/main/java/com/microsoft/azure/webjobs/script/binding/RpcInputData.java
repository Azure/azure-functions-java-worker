package com.microsoft.azure.webjobs.script.binding;

import com.google.gson.*;
import com.google.protobuf.*;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public abstract class RpcInputData<T> extends InputData {
    protected RpcInputData(String name, T value) { super(name, new Value(value)); }

    public static RpcInputData parse(ParameterBinding parameter) {
        switch (parameter.getData().getTypeVal()) {
            case String: return new RpcStringInputData(parameter.getName(), parameter.getData().getStringVal());
            case Json:   return new RpcJsonInputData(parameter.getName(), parameter.getData().getStringVal());
            case Bytes:  return new RpcBytesInputData(parameter.getName(), parameter.getData().getBytesVal());
            case Http:   return new RpcHttpInputData(parameter.getName(), parameter.getData().getHttpVal());
        }
        throw new UnsupportedOperationException("Input data type \"" + parameter.getData().getTypeVal() + "\" is not supported");
    }

    private static class RpcStringInputData extends RpcInputData<String> {
        RpcStringInputData(String name, String value) { super(name, value); }
    }

    private static class RpcJsonInputData extends RpcInputData<JsonElement> {
        RpcJsonInputData(String name, String jsonString) { super(name, new JsonParser().parse(jsonString)); }
    }

    private static class RpcBytesInputData extends RpcInputData<ByteString> {
        RpcBytesInputData(String name, ByteString value) { super(name, value); }
    }

    private static class RpcHttpInputData extends RpcInputData<RpcHttp> {
        RpcHttpInputData(String name, RpcHttp value) { super(name, value); }
    }
}
