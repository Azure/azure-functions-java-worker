package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.middleware.MiddlewareExecutionContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.ParameterResovler;

public class FunctionArgumentsResolverMiddleware implements FunctionWorkerMiddleware {
    private final ParameterResovler parameterResovler;

    public FunctionArgumentsResolverMiddleware(ParameterResovler parameterResovler) {
        this.parameterResovler = parameterResovler;
    }

    @Override
    public void invoke(MiddlewareExecutionContext context, FunctionWorkerChain next) throws Exception {
        this.parameterResovler.resolve((ExecutionContextDataSource) context);
    }
}
