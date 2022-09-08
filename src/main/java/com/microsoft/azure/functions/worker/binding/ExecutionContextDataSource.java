package com.microsoft.azure.functions.worker.binding;

import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.MiddlewareExecutionContext;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.RetryContext;
import com.microsoft.azure.functions.worker.broker.FunctionMethodExecutorImpl;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;

public final class ExecutionContextDataSource extends DataSource<ExecutionContext> implements MiddlewareExecutionContext {

    private final String invocationId;
    private final TraceContext traceContext;
    private final RetryContext retryContext;
    private final Logger logger;
    private final String funcname;
    private BindingDataStore dataStore;
    private final JavaMethodExecutor executor;
    private Object functionInstance;

    public ExecutionContextDataSource(JavaMethodExecutor executor, String invocationId, String funcname, TraceContext traceContext, RetryContext retryContext) {
        super(null, null, EXECONTEXT_DATA_OPERATIONS);
        this.invocationId = invocationId;
        this.traceContext = traceContext;
        this.retryContext = retryContext;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
        this.funcname = funcname;
        this.executor = executor;
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

    public void setDataStore(BindingDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public ClassLoader getFunctionClassLoader() {
        return executor.getClassLoader();
    }

    @Override
    public Class getFunctionClass() {
        return executor.getContainingClass();
    }

    @Override
    public Object getFunctionInstance() {
        return functionInstance;
    }

    @Override
    public void setFunctionInstance(Object obj) {
        this.functionInstance = obj;
    }

    private static final DataOperations<ExecutionContext, Object> EXECONTEXT_DATA_OPERATIONS = new DataOperations<>();
    static {
        EXECONTEXT_DATA_OPERATIONS.addGenericOperation(ExecutionContext.class, DataOperations::generalAssignment);
    }    
}