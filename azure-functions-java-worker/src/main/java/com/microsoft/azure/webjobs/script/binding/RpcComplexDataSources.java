package com.microsoft.azure.webjobs.script.binding;

import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.google.gson.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;
import static com.microsoft.azure.webjobs.script.binding.BindingDefinition.BindingType.*;

final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {
    ExecutionContextDataSource(BindingDataStore store, String invocationId) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.store = store;
        this.invocationId = invocationId;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.setValue(this);
    }

    @Override
    public String getInvocationId() { return this.invocationId; }

    @Override
    public Logger getLogger() { return this.logger; }

    @Override
    public HttpResponseMessage getResponse() {
        try {
            return this.store.getTheOnlyDefinitionOfType(HTTP).map(def -> this.getResponse(def.getName())).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public HttpResponseMessage getResponse(String name) {
        try {
            return this.store.getOrAddDataTarget(name, HttpResponseMessage.class).map(d -> (HttpResponseMessage) d.getValue()).orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    private BindingDataStore store;
    private final String invocationId;
    private final Logger logger;

    private static final DataOperations<ExecutionContext> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
}

final class RpcTriggerMetadataDataSource extends DataSource<Map<String, TypedData>> {
    RpcTriggerMetadataDataSource(Map<String, TypedData> metadata) { super(null, metadata, TRIGGER_METADATA_OPERATIONS); }

    private static final DataOperations<Map<String, TypedData>> TRIGGER_METADATA_OPERATIONS = new DataOperations<>();
}

final class RpcJsonDataSource extends DataSource<String> {
    RpcJsonDataSource(String name, String value) { super(name, value, JSON_DATA_OPERATIONS); }

    private static final DataOperations<String> JSON_DATA_OPERATIONS = new DataOperations<>();
    static {
        JSON_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, (s, t) -> new Gson().fromJson(s, t));
    }
}

final class RpcHttpRequestDataSource extends DataSource<RpcHttp> implements HttpRequestMessage {
    RpcHttpRequestDataSource(String name, RpcHttp value) {
        super(name, value, HTTP_DATA_OPERATIONS);
        this.bodyDataSource = BindingDataStore.rpcSourceFromTypedData(null, this.getValue().getBody());
    }

    @Override
    public URI getUri() { return URI.create(this.getValue().getUrl()); }
    @Override
    public String getMethod() { return this.getValue().getMethod(); }
    @Override
    public Map<String, String> getHeaders() { return this.getValue().getHeadersMap(); }
    @Override
    public Map<String, String> getQueryParameters() { return this.getValue().getQueryMap(); }
    @Override
    public Object getBody() { return this.bodyDataSource.getValue(); }

    private final DataSource<?> bodyDataSource;

    private static final DataOperations<RpcHttp> HTTP_DATA_OPERATIONS = new DataOperations<>();
    static {
        HTTP_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
}
