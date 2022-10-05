package com.microsoft.azure.functions.worker.chain;


import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;

import java.util.Iterator;
import java.util.List;

public class InvocationChain implements MiddlewareChain {
     private final Iterator<Middleware> middlewareIterator;

     public InvocationChain(List<Middleware> middlewares){
         this.middlewareIterator = middlewares.iterator();
     }

    @Override
    public void doNext(MiddlewareContext context) throws Exception {
        if (middlewareIterator.hasNext()) {
            middlewareIterator.next().invoke(context, this);
        }
    }
}
