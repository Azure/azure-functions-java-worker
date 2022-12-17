package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.FunctionMethodExecutorImpl;

public class FunctionExecutionMiddleware implements Middleware {

    private final FunctionMethodExecutorImpl functionMethodExecutor;

    public FunctionExecutionMiddleware(FunctionMethodExecutorImpl functionExecutionMiddleware) {
        this.functionMethodExecutor = functionExecutionMiddleware;
    }

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        this.functionMethodExecutor.execute((ExecutionContextDataSource) context);
    }
}
