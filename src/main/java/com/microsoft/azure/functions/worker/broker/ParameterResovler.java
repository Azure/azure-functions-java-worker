package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.model.ExecutionParameter;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Type;
import java.util.*;

public class ParameterResovler {

    public void resolve(ExecutionContextDataSource executionContextDataSource) {
        try {
            MethodBindInfo method = executionContextDataSource.getMethodBindInfo();
            BindingDataStore dataStore = executionContextDataSource.getDataStore();
            UUID outputId = UUID.randomUUID();
            executionContextDataSource.getDataStore().promoteDataTargets(outputId);
            LinkedHashMap<String, ExecutionParameter> argumentsMap = new LinkedHashMap<>();
            for (ParamBindInfo param : method.getParams()) {
                String paramName = param.getName();
                Type paramType = param.getType();
                String paramBindingNameAnnotation = param.getBindingNameAnnotation();
                Optional<BindingData> argument;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(paramType, null))) {
                    argument = dataStore.getOrAddDataTarget(outputId, paramName, paramType, false);
                    argumentsMap.put(paramName,
                            new ExecutionParameter(param.getParameter(), argument.orElseThrow(WrongMethodTypeException::new).getValue()));
                }
                else if (paramName != null && !paramName.isEmpty()) {
                    argument = dataStore.getDataByName(paramName, paramType);
                    argumentsMap.put(paramName,
                            new ExecutionParameter(param.getParameter(), argument.orElseThrow(WrongMethodTypeException::new).getValue()));
                }
                else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
                    argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType); //Optional(BindingData(Optional(null)))
                    argumentsMap.put(paramBindingNameAnnotation,
                            new ExecutionParameter(param.getParameter(), argument.orElseThrow(WrongMethodTypeException::new).getValue()));
                }
                else {
                    argument = dataStore.getDataByType(paramType);
                    argumentsMap.put("ExecutionContext",
                            new ExecutionParameter(param.getParameter(), argument.orElseThrow(WrongMethodTypeException::new).getValue()));
                }
            }
            if (!method.getEntry().getReturnType().equals(void.class) && !method.getEntry().getReturnType().equals(Void.class)) {
                dataStore.getOrAddDataTarget(outputId, BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isImplicitOutput());
            }
            executionContextDataSource.setArgumentsMap(argumentsMap);
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
        }
    }
}
