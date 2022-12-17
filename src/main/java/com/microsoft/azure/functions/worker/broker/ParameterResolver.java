package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.*;

import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.chain.ExecutionParameter;
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
    private static final ParameterResolver INSTANCE = new ParameterResolver();
    private ParameterResolver() {}

    public static ParameterResolver getInstance() {
        return INSTANCE;
    }

    public Map<String, Object> resolveArguments(ExecutionContextDataSource executionContextDataSource){
        Map<String, Object> argumentsLinedHashMap= new LinkedHashMap<>();
        UUID uuid = UUID.randomUUID();
        MethodBindInfo methodBindInfo = executionContextDataSource.getMethodBindInfo();
        BindingDataStore dataStore = executionContextDataSource.getDataStore();
        for (ParamBindInfo param : methodBindInfo.getParams()) {
            String paramName = param.getName();
            Type paramType = param.getType();
            String paramBindingNameAnnotation = param.getBindingNameAnnotation();
            Optional<BindingData> argument;
            String name;
            if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(paramType, null))) {
                argument = dataStore.getOrAddDataTarget(uuid, paramName, paramType, false);
                name = paramName;
            }
            else if (paramName != null && !paramName.isEmpty()) {
                argument = dataStore.getDataByName(paramName, paramType);
                name = paramName;
            }
            else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
                argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType);
                name = paramBindingNameAnnotation;
            }
            else {
                argument = dataStore.getDataByType(paramType);
                name = ExecutionContextDataSource.EXECUTION_CONTEXT;
            }
            BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
            executionContextDataSource.addExecutionParameter(name, new ExecutionParameter(param.getParameter(), actualArg.getValue()));
        }
        if (!methodBindInfo.getMethod().getReturnType().equals(void.class) && !methodBindInfo.getMethod().getReturnType().equals(Void.class)) {
            dataStore.getOrAddDataTarget(uuid, BindingDataStore.RETURN_NAME, methodBindInfo.getMethod().getReturnType(), methodBindInfo.hasImplicitOutput());
        }
        return argumentsLinedHashMap;
    }
}
