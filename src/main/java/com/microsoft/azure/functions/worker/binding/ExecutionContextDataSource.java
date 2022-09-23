package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.internal.MiddlewareContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;

import java.lang.annotation.Annotation;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements MiddlewareContext {
    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private final BindingDataStore dataStore;
    private final MethodBindInfo methodBindInfo;
    private final Class<?> containingClass;
    private final Map<String, Parameter> parameterMap;
    private final Map<String, String> parameterPayloadMap;
    private final Map<String, Object> middlewareInputMap = new HashMap<>();
    private Object returnValue;
    private Object middlewareOutput;

    //TODO: refactor class to have subclass dedicate to middleware to make logics clean
    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }

    public ExecutionContextDataSource(String invocationId, TraceContext traceContext, RetryContext retryContext,
                                      String funcname, BindingDataStore dataStore, MethodBindInfo methodBindInfo,
                                      Class<?> containingClass, List<ParameterBinding> inputDataList){
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.dataStore = dataStore;
        this.methodBindInfo = methodBindInfo;
        this.containingClass = containingClass;
        this.parameterMap = addParameters(methodBindInfo);
        this.parameterPayloadMap = buildParameterPayloadMap(inputDataList);
        this.setValue(this);
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

    public MethodBindInfo getMethodBindInfo() {
        return methodBindInfo;
    }

    public Class<?> getContainingClass() {
        return containingClass;
    }

    private static Map<String, Parameter> addParameters(MethodBindInfo methodBindInfo){
        Map<String, Parameter> map = new HashMap<>();
        for (ParamBindInfo paramBindInfo : methodBindInfo.getParams()) {
            map.put(paramBindInfo.getName(), paramBindInfo.getParameter());
        }
        return map;
    }

    @Override
    public Optional<String> getParameterName(String name){
        for (Map.Entry<String, Parameter> entry : this.parameterMap.entrySet()){
            if (isOrchestrationTrigger(entry.getValue(), name)){
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    private static boolean isOrchestrationTrigger(Parameter parameter, String name){
        Annotation[] annotations = parameter.getAnnotations();
        for (Annotation annotation : annotations) {
            if(annotation.annotationType().getSimpleName().equals(name)){
                return true;
            }
        }
        return false;
    }

    private static Map<String, String> buildParameterPayloadMap(List<ParameterBinding> inputDataList){
        Map<String, String> map = new HashMap<>();
        for (ParameterBinding parameterBinding : inputDataList) {
            String serializedPayload = convertToString(parameterBinding.getData());
            map.put(parameterBinding.getName(), serializedPayload);
        }
        return map;
    }

    // TODO: Refactor the code in V5 to make resolve arguments logics before middleware invocation
    private static String convertToString(TypedData data) {
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

    @Override
    public Object getParameterPayloadByName(String name) {
        return this.parameterPayloadMap.get(name);
    }

    @Override
    public void updateParameterPayloadByName(String key, Object value) {
        this.middlewareInputMap.put(key, value);
    }

    public Object getMiddlewareInputByName(String name){
        return this.middlewareInputMap.get(name);
    }

    public void setReturnValue(Object retValue) {
        this.returnValue = retValue;
    }

    @Override
    public Object getReturnValue() {
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