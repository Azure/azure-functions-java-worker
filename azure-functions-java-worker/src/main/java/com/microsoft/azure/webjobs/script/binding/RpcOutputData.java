package com.microsoft.azure.webjobs.script.binding;

import java.util.*;

import com.google.gson.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

abstract class RpcOutputData extends OutputData {
    RpcOutputData(Object retValue) { super(retValue); }
    RpcOutputData(String name, Class<?> target) throws InstantiationException, IllegalAccessException { super(name, target); }

    static RpcOutputData parse(Object retValue) {
        ReturnSupplier supplier = RETDATA_SUPPLIERS.get(retValue.getClass());
        if (supplier != null) { return supplier.get(retValue); }
        return new RpcPojoData(retValue);
    }

    static RpcOutputData parse(String name, Class<?> target) throws Exception {
        OutputSupplier supplier = OUTDATA_SUPPLIERS.get(target);
        if (supplier != null) { return supplier.get(name, target); }
        return new RpcPojoData(name, target);
    }

    @FunctionalInterface
    private interface OutputSupplier { RpcOutputData get(String name, Class<?> type) throws Exception; }

    @FunctionalInterface
    private interface ReturnSupplier { RpcOutputData get(Object ret); }

    private static final Map<Class<?>, ReturnSupplier> RETDATA_SUPPLIERS = new HashMap<Class<?>, ReturnSupplier>(){{
        put(String.class, RpcStringOutputData::new);
        put(HttpResponseMessage.class, RpcHttpOutputData::new);
    }};
    private static final Map<Class<?>, OutputSupplier> OUTDATA_SUPPLIERS = new HashMap<Class<?>, OutputSupplier>(){{
        put(String.class, RpcStringOutputData::new);
        put(HttpResponseMessage.class, RpcHttpOutputData::new);
    }};
}

class RpcStringOutputData extends RpcOutputData {
    RpcStringOutputData(Object retValue) { super(retValue); }
    RpcStringOutputData(String name, Class<?> target) throws InstantiationException, IllegalAccessException { super(name, target); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setString(this.getActualValue().getValue().toString()); }
}

class RpcPojoData extends RpcOutputData {
    RpcPojoData(Object retValue) { super(retValue); }
    RpcPojoData(String name, Class<?> target) throws InstantiationException, IllegalAccessException { super(name, target); }

    @Override
    void buildTypedData(TypedData.Builder data) { data.setJson(new Gson().toJson(this.getActualValue().getValue())); }
}

class RpcHttpOutputData extends RpcOutputData {
    RpcHttpOutputData(Object retValue) { super(retValue); }
    RpcHttpOutputData(String name, Class<?> target) throws InstantiationException, IllegalAccessException { super(name, target); }

    @Override
    void buildTypedData(TypedData.Builder data) {
        HttpResponseMessage response = (HttpResponseMessage) this.getActualValue().getValue();
        TypedData.Builder bodyBuilder = TypedData.newBuilder();
        parse(response.getBody()).buildTypedData(bodyBuilder);
        data.setHttp(RpcHttp.newBuilder().setStatusCode(response.getStatus().toString()).setBody(bodyBuilder));
    }
}
