package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;

import java.util.List;

public class InvocationChainFactory {

    private final List<FunctionWorkerMiddleware> middlewares;

    public InvocationChainFactory(List<FunctionWorkerMiddleware> middlewares) {
        this.middlewares = middlewares;
    }

    public FunctionWorkerChain create(){
        return new InvocationChain(middlewares);
    }
}
