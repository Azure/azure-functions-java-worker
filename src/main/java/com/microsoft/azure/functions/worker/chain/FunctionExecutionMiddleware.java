package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.internal.MiddlewareContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;

public class FunctionExecutionMiddleware implements FunctionWorkerMiddleware {

    private final JavaMethodExecutor functionMethodExecutor;

    public FunctionExecutionMiddleware(JavaMethodExecutor functionExecutionMiddleware) {
        this.functionMethodExecutor = functionExecutionMiddleware;
    }

    @Override
    public void invoke(MiddlewareContext context, FunctionWorkerChain next) throws Exception{
        this.functionMethodExecutor.execute((ExecutionContextDataSource) context);
    }
}
