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

import org.apache.commons.lang3.reflect.TypeUtils;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;

/**
 * Resolve a Java method overload using reflection.
 * Thread-Safety: Multiple thread.
 */
public class OverloadResolver {
    OverloadResolver() {
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
            for (ParamBindInfo param : method.params) {
                Optional<BindingData> argument = null;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(param.type, null))) {
                    argument = dataStore.getOrAddDataTarget(invokeInfo.outputsId, param.name, param.type);
                }                
                else if (param.name != null && !param.name.isEmpty()) {
                    argument = dataStore.getDataByName(param.name, param.type);
                } 
                else if (param.name == null && !param.bindingNameAnnotation.isEmpty()) {
                	argument = dataStore.getTriggerMetatDataByName(param.bindingNameAnnotation, param.type);
                }
                else {
                	argument = dataStore.getDataByType(param.type);
                }
                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
                invokeInfo.appendArgument(actualArg.getValue());
            }
            if (!method.entry.getReturnType().equals(void.class) && !method.entry.getReturnType().equals(Void.class)) {
                dataStore.getOrAddDataTarget(invokeInfo.outputsId, BindingDataStore.RETURN_NAME, method.entry.getReturnType());
            }
            return invokeInfo;
        } catch (Exception ex) {
            //TODO log
            return null;
        }
    }  
  
    private final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.entry); }
        private final UUID outputsId = UUID.randomUUID();
    }

    private final class MethodBindInfo {
        MethodBindInfo(Method m) {
            this.entry = m;
            this.params = Arrays.stream(this.entry.getParameters()).map(ParamBindInfo::new).toArray(ParamBindInfo[]::new);
        }
        private final Method entry;
        private final ParamBindInfo[] params;        
    }

    private final class ParamBindInfo {
        ParamBindInfo(Parameter param) {
            this.name = CoreTypeResolver.getBindingName(param);
            this.type = param.getParameterizedType();
            this.bindingNameAnnotation = CoreTypeResolver.getBindingNameAnnotation(param);
        }        
        
        private final String name;
        private final Type type;
        private String bindingNameAnnotation = new String("");
    }

    private final List<MethodBindInfo> candidates;
}
