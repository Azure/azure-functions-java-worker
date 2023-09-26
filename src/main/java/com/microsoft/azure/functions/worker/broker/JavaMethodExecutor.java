package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.converter.ParameterConverter;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class JavaMethodExecutor {

    private static final JavaMethodExecutor INSTANCE = new JavaMethodExecutor();

    public static JavaMethodExecutor getInstance(){
        return INSTANCE;
    }

    private JavaMethodExecutor() {}

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        Object retValue = ParameterConverter.resolveArguments(executionContextDataSource)
                .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                .invoke(executionContextDataSource.getFunctionInstance());
        executionContextDataSource.updateReturnValue(retValue);
    }
}
