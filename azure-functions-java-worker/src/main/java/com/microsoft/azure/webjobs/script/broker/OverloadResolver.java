package com.microsoft.azure.webjobs.script.broker;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.serverless.functions.OutputParameter;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.binding.*;

/**
 * m(String)
 * m(String, ExecutionContext)
 * m(String, String)
 * m(POJO)
 * m(POJO, String)
 * m(POJO, String, String)
 * m(POJO, ExecutionContext)
 * m(POJO, String, ExecutionContext)
 * m(POJO, String, String, ExecutionContext)
 * m(HttpRequestMessage)
 * m(HttpRequestMessage, ExecutionContext)
 * m(HttpRequestMessage, String, ExecutionContext)
 * m(HttpRequestMessage, String, String, ExecutionContext)
 */
class OverloadResolver {
    OverloadResolver() {
        this.candidates = new ArrayList<>();
    }

    void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    Optional<JavaMethodInvokeInfo> resolve(InputDataStore inputs, OutputDataStore outputs) {
        return Utility.singleMax(Utility.mapOptional(this.candidates, m -> this.resolve(m, inputs, outputs)),
            Comparator.comparingInt(InvokeInfoBuilder::getAssignedCount)
                    .thenComparingInt(InvokeInfoBuilder::getConvertedCount)
        ).map(InvokeInfoBuilder::build);
    }

    private Optional<InvokeInfoBuilder> resolve(MethodBindInfo method, InputDataStore inputs, OutputDataStore outputs) {
        try {
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            Utility.forEach(method.params, param -> {
                Optional<BindingData.Value<?>> argument;
                if (OutputParameter.class.isAssignableFrom(param.type)) {
                    argument = outputs.tryGenerate(param.name, param.type);
                } else {
                    argument = inputs.tryAssignAs(param.type).map(v -> { invokeInfo.assignedCount++; return v; });
                    if (!argument.isPresent()) {
                        argument = inputs.tryConvertTo(param.type).map(v -> { invokeInfo.convertedCount++; return v; });
                    }
                }
                invokeInfo.appendArgument(argument.orElseThrow(WrongMethodTypeException::new).getActual());
            });
            return Optional.of(invokeInfo);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.entry); }
        int getAssignedCount() { return this.assignedCount; }
        int getConvertedCount() { return this.convertedCount; }
        private int assignedCount = 0, convertedCount = 0;
    }

    private final class MethodBindInfo {
        MethodBindInfo(Method m) {
            this.entry = m;
            this.params = Utility.map(this.entry.getParameters(), ParamBindInfo.class, ParamBindInfo::new);
        }
        private Method entry;
        private ParamBindInfo[] params;
    }

    private final class ParamBindInfo {
        ParamBindInfo(Parameter param) {
            this.name = param.getName();
            this.type = param.getType();
        }
        private String name;
        private Class<?> type;
    }

    private List<MethodBindInfo> candidates;
}
