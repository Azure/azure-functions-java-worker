package com.microsoft.azure.functions.worker.binding;

import java.util.logging.Logger;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.worker.WorkerLogManager;

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