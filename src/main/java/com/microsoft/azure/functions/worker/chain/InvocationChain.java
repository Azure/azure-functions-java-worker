package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InvocationChain implements FunctionWorkerChain {

    private final Iterator<FunctionWorkerMiddleware> middlewareIterator;

    public InvocationChain(List<FunctionWorkerMiddleware> middlewareList) {
        this.middlewareIterator = middlewareList.iterator();
    }

    @Override
    public void doNext(ExecutionContext context) throws Exception{
        while (middlewareIterator.hasNext()) {
            middlewareIterator.next().invoke(context, this);
        }
    }

    public static class InvocationChainBuilder {

        private final List<FunctionWorkerMiddleware> middlewareCollections;

        public InvocationChainBuilder(List<FunctionWorkerMiddleware> middlewareCollections) {
            this.middlewareCollections = middlewareCollections;
        }

        public void use(FunctionWorkerMiddleware functionWorkerMiddleware){
            this.middlewareCollections.add(functionWorkerMiddleware);
        }

        public FunctionWorkerChain build(JavaMethodExecutor executor){
            List<FunctionWorkerMiddleware> middlewares = new ArrayList<>(middlewareCollections);
            middlewares.add(new FunctionExecutionMiddleware(executor));
            return new InvocationChain(middlewares);
        }
    }
}
