package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
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

    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }

    public ExecutionContextDataSource(String invocationId, TraceContext traceContext, RetryContext retryContext,
                               String funcname, BindingDataStore dataStore, MethodBindInfo methodBindInfo,
                               Class<?> containingClass){
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.dataStore = dataStore;
        this.methodBindInfo = methodBindInfo;
        this.containingClass = containingClass;
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
}