package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;

import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements ExecutionContext {

    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private final BindingDataStore dataStore;
    private final MethodBindInfo methodBindInfo;
    private final Class<?> containingClass;

    ExecutionContextDataSource(Builder builder){
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