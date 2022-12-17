package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.ParameterResolver;

public class ArgumentsResolverMiddleware implements Middleware {
    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        ParameterResolver.getInstance().resolveArguments((ExecutionContextDataSource) context);
    }
}
