package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.UUID;

import com.microsoft.azure.functions.worker.Constants;
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
    public static Optional<JavaMethodInvokeInfo> resolveArguments(ExecutionContextDataSource executionContextDataSource) {
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
                    argument = executionContextDataSource.getBindingData(paramName, paramType);
                }
                else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
                    argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType);
                }
                else {
                    argument = dataStore.getDataByType(paramType);
                }
                BindingData actualArg = argument.orElseThrow(() -> new WrongMethodTypeException(String.format(
                        "Cannot bind parameter '%s' of type %s in function '%s'. If it is an Azure SDK type, "
                      + "make sure the %s app setting is not 'false'.",
                        paramName != null ? paramName : param.getParameter().getName(), paramType,
                        executionContextDataSource.getFunctionName(), Constants.JAVA_ENABLE_SDK_TYPES)));
                invokeInfo.appendArgument(actualArg.getValue());
            }

            if (method.hasEffectiveReturnType()){
                dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), BindingDataStore.RETURN_NAME, method.getMethod().getReturnType(), true);
            }

            return invokeInfo;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }

    public static final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        private final UUID outputsId = UUID.randomUUID();
        public InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getMethod()); }

        public UUID getOutputsId() {
            return outputsId;
        }
    }
}
