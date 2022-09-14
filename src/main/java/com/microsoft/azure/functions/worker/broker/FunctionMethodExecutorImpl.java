package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import com.microsoft.azure.functions.worker.binding.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class FunctionMethodExecutorImpl implements JavaMethodExecutor {

    private final ClassLoader classLoader;

    public FunctionMethodExecutorImpl(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object functionInstance = executionContextDataSource.getFunctionInstance();
            if (functionInstance == null){
                functionInstance = executionContextDataSource.getContainingClass().newInstance();
            }
            Method method = executionContextDataSource.getMethodBindInfo().getEntry();
            Object instance = Modifier.isStatic(method.getModifiers()) ? null : functionInstance;
            Object returnValue = method.invoke(instance, executionContextDataSource.getArguments());
            executionContextDataSource.getDataStore().setDataTargetValue(BindingDataStore.RETURN_NAME, returnValue);
            executionContextDataSource.setReturnValue(returnValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }
}
