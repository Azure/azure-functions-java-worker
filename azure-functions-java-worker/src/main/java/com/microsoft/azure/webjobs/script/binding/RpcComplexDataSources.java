package com.microsoft.azure.webjobs.script.binding;

import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.google.gson.*;
import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;
import com.microsoft.azure.webjobs.script.binding.BindingData.*;

import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;

final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {
    ExecutionContextDataSource(String invocationId) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.setValue(this);
    }

    @Override
    public String getInvocationId() { return this.invocationId; }

    @Override
    public Logger getLogger() { return this.logger; }

    private final String invocationId;
    private final Logger logger;

    private static final DataOperations<ExecutionContext> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
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

final class RpcHttpRequestDataSource extends DataSource<RpcHttpRequestDataSource> implements HttpRequestMessage {
    RpcHttpRequestDataSource(String name, RpcHttp value) {
        super(name, null, HTTP_DATA_OPERATIONS);
        this.httpPayload = value;
        this.bodyDataSource = BindingDataStore.rpcSourceFromTypedData(null, this.httpPayload.getBody());
        this.fields = Arrays.asList(this.httpPayload.getHeadersMap(), this.httpPayload.getQueryMap(), this.httpPayload.getParamsMap());
        this.setValue(this);
    }

    @Override
    Optional<DataSource<?>> lookupName(MatchingLevel level, String name) {
        if (level == METADATA_NAME) {
            List<DataSource<?>> values = Utility.take(this.fields, 2, map ->
                    Optional.ofNullable(map.get(name)).map(v -> new RpcStringDataSource(name, v)));
            if (values.size() == 1) { return Optional.of(values.get(0)); }
        }
        return super.lookupName(level, name);
    }

    @Override
    public URI getUri() { return URI.create(this.httpPayload.getUrl()); }
    @Override
    public String getMethod() { return this.httpPayload.getMethod(); }
    @Override
    public Map<String, String> getHeaders() { return this.httpPayload.getHeadersMap(); }
    @Override
    public Map<String, String> getQueryParameters() { return this.httpPayload.getQueryMap(); }
    @Override
    public Object getBody() { return this.bodyDataSource.getValue(); }

    @Override
    public HttpResponseMessage createResponse() {
        return new RpcHttpDataTarget();
    }

    private final RpcHttp httpPayload;
    private final DataSource<?> bodyDataSource;
    private final List<Map<String, String>> fields;

    private static final DataOperations<RpcHttpRequestDataSource> HTTP_DATA_OPERATIONS = new DataOperations<>();
    static {
        HTTP_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
        HTTP_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (v, t) ->
                v.bodyDataSource.computeByType(t).orElseThrow(ClassCastException::new).getValue());
    }
}
