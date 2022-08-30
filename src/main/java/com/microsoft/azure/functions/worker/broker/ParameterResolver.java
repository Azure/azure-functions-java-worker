package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
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
    ParameterResolver() {
        this.candidates = new ArrayList<>();
    }

    synchronized void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    //TODO: do we need synchronized here? Seems no thread-safe issue
    public synchronized boolean hasCandidates() {
        return !this.candidates.isEmpty();
    }

    //TODO: do we need synchronized here? Seems no thread-safe issue
    public synchronized boolean hasMultipleCandidates() {
        return this.candidates.size() > 1;
    }

    //TODO: do we need synchronized here? Seems no thread-safe issue
    public synchronized MethodBindInfo getMethodBindInfo() {
        return this.candidates.get(0);
    }

    //TODO: do we need synchronized here? Seems no thread-safe issue, datastore is thread local variable.
    synchronized Optional<JavaMethodInvokeInfo> resolve(BindingDataStore dataStore) {
        InvokeInfoBuilder invoker = this.resolve(this.candidates.get(0), dataStore);
        if (invoker != null) {
            dataStore.promoteDataTargets(invoker.outputsId);
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
                    argument = dataStore.getOrAddDataTarget(invokeInfo.outputsId, paramName, paramType, false);
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
                dataStore.getOrAddDataTarget(invokeInfo.outputsId, BindingDataStore.RETURN_NAME, method.getEntry().getReturnType(), method.isHasImplicitOutput());
            }
            return invokeInfo;
        } catch (Exception ex) {
        	ExceptionUtils.rethrow(ex);
            return null;
        }
    }  
  
    public static final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getEntry()); }
        private final UUID outputsId = UUID.randomUUID();

        public UUID getOutputsId() {
            return outputsId;
        }
    }

    private final List<MethodBindInfo> candidates;
}
