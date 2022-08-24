package com.microsoft.azure.functions.worker.pipeline;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DefaultInvocationPipelineBuilder implements FunctionWorkerChain {
    private List<FunctionWorkerMiddleware> middlewareCollections = new ArrayList<>();
    private Iterator<FunctionWorkerMiddleware> middlewareIterator;

    public DefaultInvocationPipelineBuilder() {
    }

    public void use(FunctionWorkerMiddleware functionWorkerMiddleware){
        this.middlewareCollections.add(functionWorkerMiddleware);
    }

    public FunctionWorkerChain build(){
        this.middlewareIterator = middlewareCollections.iterator();
        return this;
    }

    @Override
    public void doNext(ExecutionContext context) {
        while (middlewareIterator.hasNext()) {
            middlewareIterator.next().invoke(context, this);
        }
    }

    public void setFunctionExecutionMiddleware(JavaMethodExecutor executor){
        if (middlewareCollections.size() > 0){
            FunctionExecutionMiddleware functionExecutionMiddleware = (FunctionExecutionMiddleware) this.middlewareCollections.get(this.middlewareCollections.size() - 1);
            functionExecutionMiddleware.setFunctionExecutor(executor);
        } else {
            throw new RuntimeException("There is no middleware in the invocation pipeline");
        }
    }
}
