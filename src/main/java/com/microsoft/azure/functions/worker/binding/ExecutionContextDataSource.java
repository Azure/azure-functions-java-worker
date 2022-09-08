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
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {

    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private BindingDataStore dataStore;
    private MethodBindInfo methodBindInfo;
    private Class<?> containingClass;
    private final Map<String, Parameter> parameterMap;
    private final Map<String, String> parameterPayloadMap;
    private final Map<String, Object> middlewareInputMap;
    private Object returnValue;
    private Object middlewareOutput;

    public ExecutionContextDataSource(String invocationId, String funcname, TraceContext traceContext, RetryContext retryContext) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.parameterMap = new HashMap<>();
        this.parameterPayloadMap = new HashMap<>();
        this.middlewareInputMap = new HashMap<>();
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

    public void setMethodBindInfo(MethodBindInfo methodBindInfo) {
        this.methodBindInfo = methodBindInfo;
        addParameters(methodBindInfo.getParams());
    }

    public MethodBindInfo getMethodBindInfo() {
        return methodBindInfo;
    }

    public Class<?> getContainingClass() {
        return containingClass;
    }

    public void setContainingClass(Class<?> containingClass) {
        this.containingClass = containingClass;
    }

    private void addParameters(ParamBindInfo[] paramBindInfos){
        for (ParamBindInfo paramBindInfo : paramBindInfos) {
            this.parameterMap.put(paramBindInfo.getName(), paramBindInfo.getParameter());
        }
    }

    @Override
    public Map<String, Parameter> getParameterMap() {
        return this.parameterMap;
    }

    @Override
    public Map<String, String> getParameterPayloadMap() {
        return parameterPayloadMap;
    }

    @Override
    public void setMiddlewareInput(String key, Object value) {
        this.middlewareInputMap.put(key, value);
    }

    public Map<String, Object> getMiddlewareInputMap(){
        return this.middlewareInputMap;
    }

    public void buildParameterPayloadMap(List<ParameterBinding> inputDataList){
        for (ParameterBinding parameterBinding : inputDataList) {
            String serializedPayload = convertToString(parameterBinding.getData());
            this.parameterPayloadMap.put(parameterBinding.getName(), serializedPayload);
        }
    }

    // TODO: how to serialize the input binding payload for middleware to consume?
    //  Right now for durable function middleware it only need String type
    private String convertToString(TypedData data) {
        switch (data.getDataCase()) {
            case INT:    return String.valueOf(data.getInt());
            case DOUBLE: return String.valueOf(data.getDouble());
            case STRING: return data.getString();
            case BYTES:  return data.getBytes().toString(StandardCharsets.UTF_8);
            case JSON:   return data.getJson();
            case HTTP:   return data.getHttp().toString();
            case COLLECTION_STRING: data.getCollectionString().toString();
            case COLLECTION_DOUBLE: data.getCollectionDouble().toString();
            case COLLECTION_BYTES: data.getCollectionBytes().toString();
            case COLLECTION_SINT64: data.getCollectionSint64().toString();
            default: return null;
        }
    }

    public void setReturnValue(Object retValue) {
        this.returnValue = retValue;
    }

    @Override
    public Object getReturnValue(){
        return this.returnValue;
    }

    @Override
    public void setMiddlewareOutput(Object value) {
        this.middlewareOutput = value;
    }

    public void updateOutputValue(){
        if (this.middlewareOutput == null) return;
        this.dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, this.middlewareOutput);
    }
}