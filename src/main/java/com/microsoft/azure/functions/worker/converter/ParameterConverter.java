package com.microsoft.azure.functions.worker.converter;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Type;
import java.util.Optional;

import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.broker.MethodBindInfo;
import com.microsoft.azure.functions.worker.broker.ParamBindInfo;
import com.microsoft.azure.functions.worker.invoker.MethodInvoker;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;

/**
 * Resolve a Java method overload using reflection.
 * Thread-Safety: Multiple thread.
 */
public class ParameterConverter {
    public static Optional<MethodInvoker> resolveArguments(ExecutionContextDataSource executionContextDataSource) {
        MethodInvoker invoker = resolve(executionContextDataSource);
        return invoker != null ? Optional.of(invoker) : Optional.empty();
    }

    private static MethodInvoker resolve(ExecutionContextDataSource executionContextDataSource) {
        try {
            MethodBindInfo method = executionContextDataSource.getMethodBindInfo();
            BindingDataStore dataStore = executionContextDataSource.getDataStore();
            final MethodInvoker invoker = new MethodInvoker(method.getMethod());
            for (ParamBindInfo param : method.getParams()) {
                String paramName = param.getName();
                Type paramType = param.getType();
                String paramBindingNameAnnotation = param.getBindingNameAnnotation();
                Optional<BindingData> argument;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(paramType, null))) {
                    argument = dataStore.getOrAddDataTarget(paramName, paramType, false);
                }
                else if (paramName != null && !paramName.isEmpty()) {
                    argument = executionContextDataSource.getBindingData(paramName, paramType);
                }
                else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
                    argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType);
                }
                else {
                    argument = dataStore.getDataByType(paramType);
                }
                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
                invoker.addArgument(actualArg.getValue());
            }
            // For function annotated with @HasImplicitOutput, we should allow it to send back data even function's return type is void
            // Reference to https://github.com/microsoft/durabletask-java/issues/126
            if (!method.getMethod().getReturnType().equals(void.class)
                    && !method.getMethod().getReturnType().equals(Void.class)
                    || method.hasImplicitOutput()) {
                dataStore.getOrAddDataTarget(BindingDataStore.RETURN_NAME, method.getMethod().getReturnType(), method.hasImplicitOutput());
            }
            return invoker;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }
}
