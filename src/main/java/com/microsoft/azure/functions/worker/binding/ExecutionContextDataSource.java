package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {

    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private BindingDataStore dataStore;
    //TODO: can we combine below two fields?
    private Object returnValue;
    private Object middlewareOutput;
    private final Map<String, Parameter> paramInfoMap;
    private final Map<String, Object> inputArgumentMap;
    private final Map<String, String> argumentPayloadMap;

    public ExecutionContextDataSource(String invocationId, String funcname, TraceContext traceContext, RetryContext retryContext) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.paramInfoMap = new HashMap<>();
        this.inputArgumentMap = new HashMap<>();
        this.argumentPayloadMap = new HashMap<>();
        this.setValue(this);
    }

    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }

    @Override
    public String getInvocationId() { return this.invocationId; }

    @Override
    public Logger getLogger() { return this.logger; }

    @Override
    public TraceContext getTraceContext() { return this.traceContext; }

    @Override
    public RetryContext getRetryContext() { return this.retryContext; }

    @Override
    public String getFunctionName() { return this.funcname; }

    public BindingDataStore getDataStore() {
        return dataStore;
    }

    public void setDataStore(BindingDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public Object getReturnValue() {
        return returnValue;
    }

    public void setReturnValue(Object returnValue) {
        this.returnValue = returnValue;
    }

    @Override
    public void setMiddlewareOutput(Object middlewareOutput) {
        this.middlewareOutput = middlewareOutput;
    }

    public void addParams(ParamBindInfo[] params) {
        for (ParamBindInfo param : params) {
            this.paramInfoMap.put(param.getName(), param.getParameter());
        }
    }

    @Override
    public Map<String, Parameter> getParamInfoMap() {
        return paramInfoMap;
    }

    @Override
    public void setInputArgument(String key, Object value) {
        this.inputArgumentMap.put(key, value);
    }

    public Object getInputArgumentByName(String name) {
        return this.inputArgumentMap.get(name);
    }

    public void buildArgumentPayLoad(InvocationRequest invocationRequest){
        List<ParameterBinding> inputDataList = invocationRequest.getInputDataList();
        for (ParameterBinding parameterBinding : inputDataList) {
            TypedData data = parameterBinding.getData();
            String serializedPayLoad = convertPayloadToString(data);
            this.argumentPayloadMap.put(parameterBinding.getName(), serializedPayLoad);
        }
    }

    @Override
    public Map<String, String> getArgumentPayloadMap(){
        return this.argumentPayloadMap;
    }

    //TODO: how to serialize the payload for middlware to consume
    private String convertPayloadToString(TypedData data) {
        switch (data.getDataCase()) {
            case INT:    return String.valueOf(data.getInt());
            case DOUBLE: return String.valueOf(data.getDouble());
            case STRING: return data.getString();
            case BYTES:  return data.getBytes().toString(StandardCharsets.UTF_8);
            case JSON:   return data.getJson();
//            case HTTP:   return data.getHttp();
//            case COLLECTION_STRING: data.getCollectionString();
//            case COLLECTION_DOUBLE: return new RpcCollectionDoubleDataSource(name, data.getCollectionDouble());
//            case COLLECTION_BYTES: return new RpcCollectionByteArrayDataSource(name, data.getCollectionBytes());
//            case COLLECTION_SINT64: return new RpcCollectionLongDataSource(name, data.getCollectionSint64());
//            case DATA_NOT_SET: return new RpcEmptyDataSource(name);
            default:     return null;
        }
    }

    public void buildOutput() {
        if (this.middlewareOutput == null) return;
        this.dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, this.middlewareOutput);
    }
}