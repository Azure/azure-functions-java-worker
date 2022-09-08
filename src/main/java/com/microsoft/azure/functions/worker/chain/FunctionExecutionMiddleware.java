package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.middleware.MiddlewareExecutionContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class FunctionExecutionMiddleware implements FunctionWorkerMiddleware {

    private final JavaMethodExecutor functionExecutor;

    public FunctionExecutionMiddleware(JavaMethodExecutor functionExecutor) {
        this.functionExecutor = functionExecutor;
    }

    @Override
    public void invoke(MiddlewareExecutionContext context, FunctionWorkerChain next) throws Exception{
            this.functionExecutor.execute((ExecutionContextDataSource) context);
    }
}
