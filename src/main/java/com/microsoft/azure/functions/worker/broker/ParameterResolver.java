package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;

/**
 * Resolve a Java method overload using reflection.
 * Thread-Safety: Multiple thread.
 */
public class ParameterResolver {
    public static synchronized Optional<JavaMethodInvokeInfo> resolveArguments(ExecutionContextDataSource executionContextDataSource) {
        InvokeInfoBuilder invoker = resolve(executionContextDataSource);
        if (invoker != null) {
            executionContextDataSource.getDataStore().promoteDataTargets(invoker.getOutputsId());
            return Optional.of(invoker.build());
        }
        return Optional.empty();
    }

    private static InvokeInfoBuilder resolve(ExecutionContextDataSource executionContextDataSource) {
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
                dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isImplicitOutput());
            }
            return invokeInfo;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }

    public static final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        public InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getEntry()); }
        private final UUID outputsId = UUID.randomUUID();

        public UUID getOutputsId() {
            return outputsId;
        }
    }
}
