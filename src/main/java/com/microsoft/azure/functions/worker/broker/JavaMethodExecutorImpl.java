package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.binding.*;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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
//        Object retValue = ParameterResolver.resolveArguments(executionContextDataSource)
//                .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
//                .invoke(executionContextDataSource::getFunctionInstance);
//        executionContextDataSource.updateReturnValue(retValue);

        Method method = executionContextDataSource.getMethodBindInfo().getMethod();
        Object instance = Modifier.isStatic(method.getModifiers()) ? null : executionContextDataSource.getFunctionInstance();
        try {
            Object returnValue = method.invoke(instance, executionContextDataSource.getArguments());
            executionContextDataSource.updateReturnValue(returnValue);
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
        }
    }
}
