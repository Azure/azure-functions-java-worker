package com.microsoft.azure.webjobs.script.binding;

import java.util.*;

import com.google.gson.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class RpcOutputData<T> extends DeterministicOutputData<T> {
    RpcOutputData(String name, T value) { super(name, value); }

    static DeterministicOutputData<?> parse(Object value) {
        return parse(null, value);
    }

    static DeterministicOutputData<?> parse(String name, Object value) {
        if (value == null) { return new NullOutputData(name); }
        OutputDataSupplier supplier = OUTPUT_DATA_SUPPLIERS.get(value.getClass());
        if (supplier != null) { return supplier.get(name, value); }
        return new RpcPojoData(name, value);
    }

    @FunctionalInterface
    private interface OutputDataSupplier { RpcOutputData<?> get(String name, Object value); }

    private static final Map<Class<?>, OutputDataSupplier> OUTPUT_DATA_SUPPLIERS = new HashMap<Class<?>, OutputDataSupplier>() {{
        put(Byte.class, (n, v) -> new RpcIntegerOutputData(n, ((Byte)v).longValue()));
        put(Short.class, (n, v) -> new RpcIntegerOutputData(n, ((Short)v).longValue()));
        put(Integer.class, (n, v) -> new RpcIntegerOutputData(n, ((Integer)v).longValue()));
        put(Long.class, (n, v) -> new RpcIntegerOutputData(n, (Long)v));

        put(Float.class, (n, v) -> new RpcRealNumberOutputData(n, ((Float)v).doubleValue()));
        put(Double.class, (n, v) -> new RpcRealNumberOutputData(n, (Double)v));

        put(String.class, (n, v) -> new RpcStringOutputData(n, (String)v));
        put(HttpResponseMessage.class, (n, v) -> new RpcHttpOutputData(n, (HttpResponseMessage)v));
    }};
}

class RpcIntegerOutputData extends RpcOutputData<Long> {
    RpcIntegerOutputData(String name, Long value) { super(name, value); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setInt(this.getActualValue()); }
}

class RpcRealNumberOutputData extends RpcOutputData<Double> {
    RpcRealNumberOutputData(String name, Double value) { super(name, value); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setDouble(this.getActualValue()); }
}

class RpcStringOutputData extends RpcOutputData<String> {
    RpcStringOutputData(String name, String value) { super(name, value); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setString(this.getActualValue()); }
}

class RpcPojoData extends RpcOutputData<Object> {
    RpcPojoData(String name, Object value) { super(name, value); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setJson(new Gson().toJson(this.getActualValue())); }
}

class RpcHttpOutputData extends RpcOutputData<HttpResponseMessage> {
    RpcHttpOutputData(String name, HttpResponseMessage value) { super(name, value); }

    @Override
    void buildTypedData(TypedData.Builder data) {
        TypedData.Builder bodyBuilder = TypedData.newBuilder();
        parse(this.getActualValue().getBody()).buildTypedData(bodyBuilder);
        data.setHttp(RpcHttp.newBuilder().setStatusCode(this.getActualValue().getStatus().toString()).setBody(bodyBuilder));
    }
}
