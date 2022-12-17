package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.binding.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
            Method method = executionContextDataSource.getMethodBindInfo().getMethod();
            Object instance = Modifier.isStatic(method.getModifiers()) ? null : executionContextDataSource.getFunctionInstance();
            try {
                Object returnValue = method.invoke(instance, executionContextDataSource.getArguments());
                executionContextDataSource.updateReturnValue(returnValue);
            } catch (Exception ex) {
                ExceptionUtils.rethrow(ex);
            }
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }
}
