package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class FunctionMethodExecutorImpl implements JavaMethodExecutor {

    private static final FunctionMethodExecutorImpl INSTANCE = new FunctionMethodExecutorImpl();

    private ClassLoader classLoader;

    private FunctionMethodExecutorImpl() {}

    public static FunctionMethodExecutorImpl getInstance(){
        return INSTANCE;
    }

    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object retValue = this.resolve(executionContextDataSource)
                    .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                    .invoke(() -> executionContextDataSource.getContainingClass().newInstance());
            executionContextDataSource.getDataStore().setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

    private synchronized Optional<JavaMethodInvokeInfo> resolve(ExecutionContextDataSource executionContextDataSource) {
        BindingDataStore dataStore = executionContextDataSource.getDataStore();
        InvokeInfoBuilder invoker = this.resolve(executionContextDataSource.getMethodBindInfo(), dataStore);
        if (invoker != null) {
            dataStore.promoteDataTargets(invoker.getOutputsId());
            return Optional.of(invoker.build());
        }
        return Optional.empty();
    }

    private InvokeInfoBuilder resolve(MethodBindInfo method, BindingDataStore dataStore) {
        try {
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            for (ParamBindInfo param : method.getParams()) {
                String paramName = param.getName();
                Type paramType = param.getType();
                String paramBindingNameAnnotation = param.getBindingNameAnnotation();
                Optional<BindingData> argument;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(paramType, null))) {
                    argument = dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), paramName, paramType, false);
                }
                else if (paramName != null && !paramName.isEmpty()) {
                    argument = dataStore.getDataByName(paramName, paramType);
                }
                else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
                    argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType);
                }
                else {
                    argument = dataStore.getDataByType(paramType);
                }
                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
                invokeInfo.appendArgument(actualArg.getValue());
            }
            if (!method.getEntry().getReturnType().equals(void.class) && !method.getEntry().getReturnType().equals(Void.class)) {
                dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isHasImplicitOutput());
            }
            return invokeInfo;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }
}
