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

//    public static Optional<JavaMethodInvokeInfo> resolveArguments(ExecutionContextDataSource executionContextDataSource) {
//        InvokeInfoBuilder invoker = resolve(executionContextDataSource);
//        if (invoker != null) {
//            executionContextDataSource.getDataStore().promoteDataTargets(invoker.getOutputsId());
//            return Optional.of(invoker.build());
//        }
//        return Optional.empty();
//    }
//
//    private static InvokeInfoBuilder resolve(ExecutionContextDataSource executionContextDataSource) {
//        try {
//            MethodBindInfo method = executionContextDataSource.getMethodBindInfo();
//            BindingDataStore dataStore = executionContextDataSource.getDataStore();
//            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
//            for (ParamBindInfo param : method.getParams()) {
//                String paramName = param.getName();
//                Type paramType = param.getType();
//                String paramBindingNameAnnotation = param.getBindingNameAnnotation();
//                Optional<BindingData> argument;
//                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(paramType, null))) {
//                    argument = dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), paramName, paramType, false);
//                }
//                else if (paramName != null && !paramName.isEmpty()) {
//                    argument = executionContextDataSource.getBindingData(paramName, paramType);
//                }
//                else if (paramName == null && !paramBindingNameAnnotation.isEmpty()) {
//                    argument = dataStore.getTriggerMetatDataByName(paramBindingNameAnnotation, paramType);
//                }
//                else {
//                    argument = dataStore.getDataByType(paramType);
//                }
//                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
//                invokeInfo.appendArgument(actualArg.getValue());
//            }
//            if (!method.getMethod().getReturnType().equals(void.class) && !method.getMethod().getReturnType().equals(Void.class)) {
//                dataStore.getOrAddDataTarget(invokeInfo.getOutputsId(), BindingDataStore.RETURN_NAME, method.getMethod().getReturnType(), method.hasImplicitOutput());
//            }
//            return invokeInfo;
//        } catch (Exception ex) {
//            ExceptionUtils.rethrow(ex);
//            return null;
//        }
//    }
//
//    public static final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
//        public InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getMethod()); }
//        private final UUID outputsId = UUID.randomUUID();
//
//        public UUID getOutputsId() {
//            return outputsId;
//        }
//    }
}
