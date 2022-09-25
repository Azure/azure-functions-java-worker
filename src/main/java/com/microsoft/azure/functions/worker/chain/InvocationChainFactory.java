package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.middleware.FunctionMiddlewareChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;

import java.util.List;

public class InvocationChainFactory {

    private final List<FunctionWorkerMiddleware> middlewares;

    public InvocationChainFactory(List<FunctionWorkerMiddleware> middlewares) {
        this.middlewares = middlewares;
    }

    public FunctionMiddlewareChain create(){
        return new InvocationChain(middlewares);
    }
}
