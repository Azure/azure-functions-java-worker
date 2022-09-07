package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.*;

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

    private final List<MethodBindInfo> candidates;
    ParameterResolver() {
        this.candidates = new ArrayList<>();
    }

    synchronized void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    public synchronized boolean hasCandidates() {
        return !this.candidates.isEmpty();
    }

    public synchronized boolean hasMultipleCandidates() {
        return this.candidates.size() > 1;
    }

    public List<MethodBindInfo> getCandidates() {
        return candidates;
    }

//    synchronized Optional<JavaMethodInvokeInfo> resolve(BindingDataStore dataStore) {
//        InvokeInfoBuilder invoker = this.resolve(this.candidates.get(0), dataStore);
//        if (invoker != null) {
//            dataStore.promoteDataTargets(invoker.outputsId);
//            return Optional.of(invoker.build());
//        }
//        return Optional.empty();
//    }

    synchronized Optional<JavaMethodInvokeInfo> resolve(ExecutionContextDataSource executionContextDataSource) {
        InvokeInfoBuilder invoker = this.resolve(this.candidates.get(0), executionContextDataSource);
        if (invoker != null) {
            executionContextDataSource.getDataStore().promoteDataTargets(invoker.outputsId);
            return Optional.of(invoker.build());
        }
        return Optional.empty();
    }

//    private InvokeInfoBuilder resolve(MethodBindInfo method, BindingDataStore dataStore) {
//        try {
//            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
//            for (ParamBindInfo param : method.getParams()) {
//                Optional<BindingData> argument = null;
//                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(param.getType(), null))) {
//                    argument = dataStore.getOrAddDataTarget(invokeInfo.outputsId, param.getName(), param.getType(), false);
//                }
//                else if (param.getName() != null && !param.getName().isEmpty()) {
//                    argument = dataStore.getDataByName(param.getName(), param.getType());
//                }
//                else if (param.getName() == null && !param.getBindingNameAnnotation().isEmpty()) {
//                	argument = dataStore.getTriggerMetatDataByName(param.getBindingNameAnnotation(), param.getType());
//                }
//                else {
//                	argument = dataStore.getDataByType(param.getType());
//                }
//                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
//                invokeInfo.appendArgument(actualArg.getValue());
//            }
//            if (!method.getEntry().getReturnType().equals(void.class) && !method.getEntry().getReturnType().equals(Void.class)) {
//                dataStore.getOrAddDataTarget(invokeInfo.outputsId, BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isHasImplicitOutput());
//            }
//            return invokeInfo;
//        } catch (Exception ex) {
//        	ExceptionUtils.rethrow(ex);
//            return null;
//        }
//    }

    private InvokeInfoBuilder resolve(MethodBindInfo method, ExecutionContextDataSource executionContextDataSource) {
        try {
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            BindingDataStore dataStore = executionContextDataSource.getDataStore();
            for (ParamBindInfo param : method.getParams()) {
                Optional<BindingData> argument = null;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(param.getType(), null))) {
                    argument = dataStore.getOrAddDataTarget(invokeInfo.outputsId, param.getName(), param.getType(), false);
                }
                else if (param.getName() != null && !param.getName().isEmpty()) {
                    // Get middleware updated input first
                    argument = buildArgumentFromInputMap(param.getName(), executionContextDataSource);
                    // if nothing get from middleware, fallback to normal argument building logics.
                    if (!argument.isPresent()){
                        argument = dataStore.getDataByName(param.getName(), param.getType());
                    }
                }
                else if (param.getName() == null && !param.getBindingNameAnnotation().isEmpty()) {
                    argument = dataStore.getTriggerMetatDataByName(param.getBindingNameAnnotation(), param.getType());
                }
                else {
                    argument = dataStore.getDataByType(param.getType());
                }
                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
                invokeInfo.appendArgument(actualArg.getValue());
            }
            if (!method.getEntry().getReturnType().equals(void.class) && !method.getEntry().getReturnType().equals(Void.class)) {
                dataStore.getOrAddDataTarget(invokeInfo.outputsId, BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isHasImplicitOutput());
            }
            return invokeInfo;
        } catch (Exception ex) {
            ExceptionUtils.rethrow(ex);
            return null;
        }
    }

    private Optional<BindingData> buildArgumentFromInputMap(String name, ExecutionContextDataSource executionContextDataSource) {
        Object inputByName = executionContextDataSource.getInputArgumentByName(name);
        if (inputByName == null) return Optional.empty();
        BindingData bindingData = new BindingData(inputByName);
        return Optional.of(bindingData);
    }

    private static final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getEntry()); }
        private final UUID outputsId = UUID.randomUUID();
    }
}
