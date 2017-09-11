package com.microsoft.azure.webjobs.script.broker;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.binding.*;

/**
 * Resolve a Java method overload using reflection.
 * Thread-Safety: Multiple thread.
 */
class OverloadResolver {
    OverloadResolver() {
        this.candidates = new ArrayList<>();
    }

    synchronized void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    synchronized Optional<JavaMethodInvokeInfo> resolve(InputDataStore inputs) {
        return Utility.singleMax(Utility.mapOptional(this.candidates, m -> this.resolve(m, inputs)),
            Comparator.comparingInt(InvokeInfoBuilder::getNamedParamCount)
                    .thenComparingInt(InvokeInfoBuilder::getAssignedCount)
                    .thenComparingInt(InvokeInfoBuilder::getConvertedCount)
        ).map(InvokeInfoBuilder::build);
    }

    private Optional<InvokeInfoBuilder> resolve(MethodBindInfo method, InputDataStore inputs) {
        try {
            final OutputDataStore outputs = new OutputDataStore();
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            Utility.forEach(method.params, param -> {
                Optional<BindingData.Value<?>> argument;
                if (OutputParameter.class.isAssignableFrom(param.type)) {
                    argument = outputs.tryGenerateParameter(param.name, param.type);
                } else if (param.name != null && !param.name.isEmpty()) {
                    argument = inputs.tryLookupByName(param.name, param.type).map(v -> { invokeInfo.namedParamCount++; return v; });
                } else {
                    argument = inputs.tryAssignAs(param.type).map(v -> { invokeInfo.assignedCount++; return v; });
                    if (!argument.isPresent()) { argument = inputs.tryConvertTo(param.type).map(v -> { invokeInfo.convertedCount++; return v; }); }
                }
                invokeInfo.appendArgument(argument.orElseThrow(WrongMethodTypeException::new).getActual());
            });
            invokeInfo.setOutputs(outputs);
            return Optional.of(invokeInfo);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.entry); }
        int getNamedParamCount() { return this.namedParamCount; }
        int getAssignedCount() { return this.assignedCount; }
        int getConvertedCount() { return this.convertedCount; }
        private int namedParamCount = 0, assignedCount = 0, convertedCount = 0;
    }

    private final class MethodBindInfo {
        MethodBindInfo(Method m) {
            this.entry = m;
            this.params = Utility.map(this.entry.getParameters(), ParamBindInfo.class, ParamBindInfo::new);
        }
        private final Method entry;
        private final ParamBindInfo[] params;
    }

    private final class ParamBindInfo {
        ParamBindInfo(Parameter param) {
            Bind bindInfo = param.getAnnotation(Bind.class);
            this.name = (bindInfo != null ? bindInfo.value() : null);
            this.type = param.getType();
        }
        private final String name;
        private final Class<?> type;
    }

    private final List<MethodBindInfo> candidates;
}
