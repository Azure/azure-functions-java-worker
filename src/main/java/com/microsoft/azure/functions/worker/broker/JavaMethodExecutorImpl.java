package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.binding.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class JavaMethodExecutorImpl implements JavaMethodExecutor {

    private static final JavaMethodExecutorImpl INSTANCE = new JavaMethodExecutorImpl();

    public static JavaMethodExecutorImpl getInstance(){
        return INSTANCE;
    }

    private JavaMethodExecutorImpl () {}

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        Object retValue = ParameterResolver.resolveArguments(executionContextDataSource)
                .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                .invoke(executionContextDataSource::getFunctionInstance);
        executionContextDataSource.updateReturnValue(retValue);
    }
}
