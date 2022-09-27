package com.microsoft.azure.functions.worker.chain;


import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;

import java.util.List;

public class InvocationChainFactory {

    private final List<Middleware> middlewares;

    public InvocationChainFactory(List<Middleware> middlewares) {
        this.middlewares = middlewares;
    }

    public MiddlewareChain create(){
        return new InvocationChain(middlewares);
    }
}
