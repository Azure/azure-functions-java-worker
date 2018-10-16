package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;
import java.util.stream.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.*;

import org.apache.commons.lang3.reflect.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.binding.BindingData.*;

import static com.microsoft.azure.functions.worker.binding.BindingData.MatchingLevel.*;

final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {
    ExecutionContextDataSource(String invocationId, String funcname) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.setValue(this);
    }

    @Override
    public String getInvocationId() { return this.invocationId; }

    @Override
    public Logger getLogger() { return this.logger; }

    @Override
    public String getFunctionName() { return this.funcname; }

    private final String invocationId;
    private final Logger logger;
    private final String funcname;

    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGuardOperation(TYPE_ASSIGNMENT, DataOperations::generalAssignment);
    }
}

final class RpcTriggerMetadataDataSource extends DataSource<Map<String, TypedData>> {
    RpcTriggerMetadataDataSource(Map<String, TypedData> metadata) {
        super(null, metadata, TRIGGER_METADATA_OPERATIONS);
    }

    @Override
    Optional<DataSource<?>> lookupName(MatchingLevel level, String name) {
        if (level == TRIGGER_METADATA_NAME) {
            return Optional.ofNullable(this.getValue().get(name)).map(v -> BindingDataStore.rpcSourceFromTypedData(name, v));
        }
        return super.lookupName(level, name);
    }

    private static final DataOperations<Map<String, TypedData>, Object> TRIGGER_METADATA_OPERATIONS = new DataOperations<>();
}

final class RpcJsonDataSource extends DataSource<String> {
    RpcJsonDataSource(String name, String value) { super(name, value, JSON_DATA_OPERATIONS); }

    private static final ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
    private static final DataOperations<String, Object> JSON_DATA_OPERATIONS = new DataOperations<>();

    static {
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
        RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

        JSON_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, String.class, s -> s);
        JSON_DATA_OPERATIONS.addOperation(TYPE_STRICT_CONVERSION, String[].class, s -> RELAXED_JSON_MAPPER.readValue(s, String[].class));
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
            List<DataSource<?>> values = this.fields.stream()
                .map(f -> f.get(name))
                .filter(Objects::nonNull)
                .limit(2)
                .map(v -> new RpcStringDataSource(name, v))
                .collect(Collectors.toList());
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
        public HttpMethod getHttpMethod() { return HttpMethod.value(this.parentDataSource.httpPayload.getMethod()); }
        @Override
        public Map<String, String> getHeaders() { return this.parentDataSource.httpPayload.getHeadersMap(); }
        @Override
        public Map<String, String> getQueryParameters() { return this.parentDataSource.httpPayload.getQueryMap(); }
        @Override
        public Object getBody() { return this.body; }

        @Override
        public HttpResponseMessage.Builder createResponseBuilder(HttpStatusType status) {
            return new RpcHttpDataTarget().status(status);
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
