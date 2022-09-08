package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.FunctionMethodExecutorImpl;

public class FunctionExecutionMiddleware implements FunctionWorkerMiddleware {

    private final FunctionMethodExecutorImpl functionMethodExecutor;

    public FunctionExecutionMiddleware(FunctionMethodExecutorImpl functionExecutionMiddleware) {
        this.functionMethodExecutor = functionExecutionMiddleware;
    }

    @Override
    public void invoke(ExecutionContext context, FunctionWorkerChain next) throws Exception{
        this.functionMethodExecutor.execute((ExecutionContextDataSource) context);
    }
}
