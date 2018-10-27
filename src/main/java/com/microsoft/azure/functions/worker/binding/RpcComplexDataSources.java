package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;

import org.apache.commons.lang3.reflect.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

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
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }
}

final class RpcTriggerMetadataDataSource extends DataSource<Map<String, TypedData>> {
    RpcTriggerMetadataDataSource(Map<String, TypedData> metadata) {
        super(null, metadata, TRIGGER_METADATA_OPERATIONS);
    }

    @Override
    Optional<DataSource<?>> lookupName(String name) {
      return Optional.ofNullable(this.getValue().get(name)).map(v -> BindingDataStore.rpcSourceFromTypedData(name, v));
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

        JSON_DATA_OPERATIONS.addOperation(String.class, s -> s);
        JSON_DATA_OPERATIONS.addOperation(String[].class, s -> RELAXED_JSON_MAPPER.readValue(s, String[].class));        
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
        
        @Override
		public Builder createResponseBuilder(HttpStatus status) {
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
        HTTP_DATA_OPERATIONS.addGenericOperation(HttpRequestMessage.class, (v, t) -> {            
                Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(t, HttpRequestMessage.class);
                Type actualType = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
                BindingData bodyData = v.bodyDataSource.computeByType(actualType).orElseThrow(ClassCastException::new);
                return new HttpRequestMessageImpl(v, bodyData.getValue());
        });        
    }
}
