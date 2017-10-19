package com.microsoft.azure.webjobs.script.binding;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import org.apache.commons.lang3.reflect.*;

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

    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
}

final class RpcTriggerMetadataDataSource extends DataSource<Map<String, TypedData>> {
    RpcTriggerMetadataDataSource(Map<String, TypedData> metadata) { super(null, metadata, TRIGGER_METADATA_OPERATIONS); }

    private static final DataOperations<Map<String, TypedData>, Object> TRIGGER_METADATA_OPERATIONS = new DataOperations<>();
}

final class RpcJsonDataSource extends DataSource<String> {
    RpcJsonDataSource(String name, String value) { super(name, value, JSON_DATA_OPERATIONS); }

    private static final ObjectMapper STRICT_JSON_MAPPER = new ObjectMapper();
    private static final ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
    private static final DataOperations<String, Object> JSON_DATA_OPERATIONS = new DataOperations<>();
    static {
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);

        JSON_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, (s, t) -> STRICT_JSON_MAPPER.readValue(s, TypeUtils.getRawType(t, null)));
        JSON_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, String.class, s -> s);
        JSON_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (s, t) -> RELAXED_JSON_MAPPER.readValue(s, TypeUtils.getRawType(t, null)));
    }
}

final class RpcHttpRequestDataSource extends DataSource<RpcHttpRequestDataSource> {
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
            if (values.size() == 1) {
                return Optional.of(values.get(0));
            }
        }
        return super.lookupName(level, name);
    }

    private static class HttpRequestMessageImpl implements HttpRequestMessage {
        private HttpRequestMessageImpl(RpcHttpRequestDataSource parentDataSource, Object body) {
            this.parentDataSource = parentDataSource;
            this.body = body;
        }

        @Override
        public URI getUri() { return URI.create(this.parentDataSource.httpPayload.getUrl()); }
        @Override
        public String getMethod() { return this.parentDataSource.httpPayload.getMethod(); }
        @Override
        public Map<String, String> getHeaders() { return this.parentDataSource.httpPayload.getHeadersMap(); }
        @Override
        public Map<String, String> getQueryParameters() { return this.parentDataSource.httpPayload.getQueryMap(); }
        @Override
        public Object getBody() { return this.body; }

        @Override
        public HttpResponseMessage createResponse(int status, Object body) {
            RpcHttpDataTarget response = new RpcHttpDataTarget();
            response.setStatus(status);
            response.setBody(body);
            return response;
        }

        private RpcHttpRequestDataSource parentDataSource;
        private Object body;
    }

    private final RpcHttp httpPayload;
    private final DataSource<?> bodyDataSource;
    private final List<Map<String, String>> fields;

    private static final DataOperations<RpcHttpRequestDataSource, Object> HTTP_DATA_OPERATIONS = new DataOperations<>();
    static {
        HTTP_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, (v, t) -> {
            if (HttpRequestMessage.class.equals(TypeUtils.getRawType(t, null))) {
                Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(t, HttpRequestMessage.class);
                Type actualType = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
                BindingData bodyData = v.bodyDataSource.computeByType(actualType).orElseThrow(ClassCastException::new);
                return new HttpRequestMessageImpl(v, bodyData.getValue());
            }
            throw new ClassCastException();
        });
        HTTP_DATA_OPERATIONS.addGuardOperation(TYPE_RELAXED_CONVERSION, (v, t) ->
                v.bodyDataSource.computeByType(t).orElseThrow(ClassCastException::new).getNullSafeValue());
    }
}
