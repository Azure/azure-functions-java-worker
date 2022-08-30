package com.microsoft.azure.functions.worker.pipeline;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.middleware.FunctionWorkerChain;
import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutor;
import com.microsoft.azure.functions.worker.broker.JavaMethodExecutorImpl;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class FunctionExecutionMiddleware implements FunctionWorkerMiddleware {

    private final JavaMethodExecutor functionExecutor = new JavaMethodExecutorImpl();

    @Override
    public void invoke(ExecutionContext context, FunctionWorkerChain next) {
        try {
            this.functionExecutor.execute((ExecutionContextDataSource) context);
        } catch (Exception e) {
            ExceptionUtils.rethrow(e);
        }
    }

//    public void setFunctionExecutor(JavaMethodExecutor functionExecutor) {
//        this.functionExecutor = functionExecutor;
//    }
}
