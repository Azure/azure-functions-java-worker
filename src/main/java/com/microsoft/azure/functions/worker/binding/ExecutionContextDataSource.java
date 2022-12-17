package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.model.ExecutionParameter;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;


import java.lang.reflect.Parameter;
import java.util.*;
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
    private LinkedHashMap<String, ExecutionParameter> argumentsMap;
    private Object returnValue;
    private Object middlewareOutput;
    private Object functionInstance;

    public ExecutionContextDataSource(Builder builder){
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = builder.invocationId;
        this.traceContext = builder.traceContext;
        this.retryContext = builder.retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(this.invocationId);
        this.funcname = builder.funcname;
        this.dataStore = builder.dataStore;
        this.methodBindInfo = builder.methodBindInfo;
        this.containingClass = builder.containingClass;
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

    public MethodBindInfo getMethodBindInfo() {
        return methodBindInfo;
    }

    public Class<?> getContainingClass() {
        return containingClass;
    }

    //TODO: cannot guarantee this argumentsMap is immutable.
    public void setArgumentsMap(LinkedHashMap<String, ExecutionParameter> argumentsMap) {
        this.argumentsMap = argumentsMap;
    }

    public Object[] getArguments(){
        if (this.argumentsMap == null || this.argumentsMap.isEmpty()) return new Object[0];
        Object[] arguments = new Object[this.argumentsMap.size()];
        int idx = 0;
        for (Map.Entry<String, ExecutionParameter> entry : this.argumentsMap.entrySet()){
            arguments[idx++] = entry.getValue().getPayload();
        }
        return arguments;
    }

    public Map<String, Parameter> getParameterMap(){
        Map<String, Parameter> map = new HashMap<>();
        for (Map.Entry<String, ExecutionParameter> entry : this.argumentsMap.entrySet()){
            map.put(entry.getKey(), entry.getValue().getParameter());
        }
        return map;
    }

    public Object getParameterPayloadByName(String name){
        return this.argumentsMap.get(name).getPayload();
    }

    public void updateParameterPayloadByName(String name, Object payload){
        this.argumentsMap.get(name).setPayload(payload);
    }

    public void setReturnValue(Object retValue) {
        this.returnValue = retValue;
    }

    @Override
    public String getParameterName(String annotationSimpleClassName) {
        return null;
    }

    @Override
    public Object getParameterValue(String name) {
        return null;
    }

    @Override
    public void updateParameterValue(String name, Object value) {

    }

    @Override
    public Object getReturnValue(){
        return this.returnValue;
    }

    @Override
    public void updateReturnValue(Object returnValue) {

    }

    public void setMiddlewareOutput(Object value) {
        this.middlewareOutput = value;
    }

    public void updateOutputValue(){
        if (this.middlewareOutput == null) return;
        this.dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, this.middlewareOutput);
    }

    public void setFunctionInstance(Object functionInstance) {
        this.functionInstance = functionInstance;
    }

    public Object getFunctionInstance() {
        return functionInstance;
    }

    public static class Builder{
        private String invocationId;
        private TraceContext traceContext;
        private RetryContext retryContext;
        private String funcname;
        private BindingDataStore dataStore;
        private MethodBindInfo methodBindInfo;
        private Class<?> containingClass;

        public Builder invocationId(String invocationId){
            this.invocationId = invocationId;
            return this;
        }

        public Builder traceContext(TraceContext traceContext){
            this.traceContext = traceContext;
            return this;
        }

        public Builder retryContext(RetryContext retryContext){
            this.retryContext = retryContext;
            return this;
        }

        public Builder funcname(String funcname){
            this.funcname = funcname;
            return this;
        }

        public Builder dataStore(BindingDataStore dataStore){
            this.dataStore = dataStore;
            return this;
        }

        public Builder methodBindInfo(MethodBindInfo methodBindInfo){
            this.methodBindInfo = methodBindInfo;
            return this;
        }

        public Builder containingClass(Class<?> containingClass){
            this.containingClass = containingClass;
            return this;
        }

        public ExecutionContextDataSource build(){
            return new ExecutionContextDataSource(this);
        }
    }
}