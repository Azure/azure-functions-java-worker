package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

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
                    .invoke(() -> executionContextDataSource.getContainingClass().newInstance());
            executionContextDataSource.getDataStore().setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }
}
