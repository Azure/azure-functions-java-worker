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
public class FunctionMethodExecutorImpl implements JavaMethodExecutor {

    private final ClassLoader classLoader;

    public FunctionMethodExecutorImpl(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public void execute(ExecutionContextDataSource executionContextDataSource) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object retValue = this.resolveWrapper(executionContextDataSource)
                    .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                    //TODO: Should we only create one instance at function load time, instead of create the new instance every invocation
                    // How does this affects DI framework
                    .invoke(() -> executionContextDataSource.getContainingClass().newInstance());
            executionContextDataSource.getDataStore().setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
            executionContextDataSource.setReturnValue(retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

    private synchronized Optional<JavaMethodInvokeInfo> resolveWrapper(ExecutionContextDataSource executionContextDataSource) {
        InvokeInfoBuilder invoker = this.resolve(executionContextDataSource);
        if (invoker != null) {
            executionContextDataSource.getDataStore().promoteDataTargets(invoker.getOutputsId());
            return Optional.of(invoker.build());
        }
        return Optional.empty();
    }

    private InvokeInfoBuilder resolve(ExecutionContextDataSource executionContextDataSource) {
        try {
            MethodBindInfo method = executionContextDataSource.getMethodBindInfo();
            BindingDataStore dataStore = executionContextDataSource.getDataStore();
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
                    //check if middleware set input, if not fallback to normal logics for build the argument.
                    argument = getMiddlewareInput(executionContextDataSource.getMiddlewareInputMap(), paramName);
                    if (!argument.isPresent()){
                        argument = dataStore.getDataByName(paramName, paramType);
                    }
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
                dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isImplicitOutput());
            }
            return invokeInfo;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }

    private Optional<BindingData> getMiddlewareInput(Map<String, Object> middlewareInputMap, String paramName) {
        Object input = middlewareInputMap.get(paramName);
        if (input == null) return Optional.empty();
        return Optional.of(new BindingData(input));
    }
}
