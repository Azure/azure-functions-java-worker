package com.microsoft.azure.webjobs.script.broker;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;

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
public class OverloadResolver {
    public OverloadResolver() {
        this.candidates = new ArrayList<>();
    }

    public void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    public Optional<JavaMethodInvokeInfo> resolve(InputDataStore inputs, OutputDataStore outputs) {
        this.inputs = inputs;
        this.outputs = outputs;
        return Utility.singleMax(Utility.map(this.candidates, this::resolve),
            Comparator.comparingInt(InvokeInfoBuilder::getAssignedCount)
                    .thenComparingInt(InvokeInfoBuilder::getConvertedCount)
        ).map(InvokeInfoBuilder::build);
    }

    private Optional<InvokeInfoBuilder> resolve(MethodBindInfo method) {
        try {
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            Utility.forEach(method.params, param -> {
                invokeInfo.appendArgument(this.inputs.tryAssignAs(param.type).map(v -> { invokeInfo.assignedCount++; return v; })
                        .orElseGet(() -> this.inputs.tryConvertTo(param.type).map(v -> { invokeInfo.convertedCount++; return v; })
                        .orElseThrow(WrongMethodTypeException::new))
                        .getActual());
            });
            return Optional.of(invokeInfo);
        } catch (Exception ex) {
            return Optional.empty();
        }

        /*
        for (int i = 0; i < method.getParamsCount(); i++) {
            // If input data
            if (method.getBind(i) != null) {
                // Try query name
                // If succeeded, increment name count
                // If failed, return failed.
            } else {
                // Try assign
                // If succeeded, increment assignment count
                // If failed, try convert
                // If succeeded, increment convert count
                // If failed, return failed
            }
            // If output data
            // Ensure bind exists
            // Generate output binding
            // If failed, return failed.
        }
        */
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
            this.params = Utility.map(this.entry.getParameters(), ParamBindInfo::new);
        }
        private Method entry;
        private ParamBindInfo[] params;
    }

    private final class ParamBindInfo {
        ParamBindInfo(Parameter param) {
            this.type = param.getType();
        }
        private Class<?> type;
    }

    private List<MethodBindInfo> candidates;
    private InputDataStore inputs;
    private OutputDataStore outputs;
}
