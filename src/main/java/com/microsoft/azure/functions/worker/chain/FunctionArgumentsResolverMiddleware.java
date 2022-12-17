package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.ParameterResovler;

public class FunctionArgumentsResolverMiddleware implements Middleware {
    private final ParameterResovler parameterResovler;

    public FunctionArgumentsResolverMiddleware(ParameterResovler parameterResovler) {
        this.parameterResovler = parameterResovler;
    }

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        this.parameterResovler.resolve((ExecutionContextDataSource) context);
    }
}
