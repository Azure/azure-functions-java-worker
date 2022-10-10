package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.binding.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class EnhancedJavaMethodExecutorImpl implements JavaMethodExecutor {

    private final ClassLoader classLoader;

    public EnhancedJavaMethodExecutorImpl(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object retValue = ParameterResolver.resolveArguments(executionContextDataSource)
                    .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                    .invoke(executionContextDataSource::getFunctionInstance);
            executionContextDataSource.updateReturnValue(retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }
}
