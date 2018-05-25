package com.microsoft.azure.functions.worker.broker;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import com.google.common.collect.*;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.worker.binding.*;
import org.apache.commons.lang3.reflect.*;

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

    synchronized boolean hasCandidates() {
        return !this.candidates.isEmpty();
    }

    synchronized Optional<JavaMethodInvokeInfo> resolve(BindingDataStore dataStore) {
        Comparator<InvokeInfoBuilder> overloadComparator = Comparator
                .<InvokeInfoBuilder>comparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.BINDING_NAME.getIndex()])
                .thenComparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.TRIGGER_METADATA_NAME.getIndex()])
                .thenComparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.METADATA_NAME.getIndex()])
                .thenComparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.TYPE_ASSIGNMENT.getIndex()])
                .thenComparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.TYPE_STRICT_CONVERSION.getIndex()])
                .thenComparingInt(info -> info.matchingLevelCount[BindingData.MatchingLevel.TYPE_RELAXED_CONVERSION.getIndex()]);
        List<InvokeInfoBuilder> possibleInvokes = this.candidates.stream()
            .map(m -> this.resolve(m, dataStore))
            .flatMap(o -> o.map(Stream::of).orElseGet(Stream::empty))
            .collect(Comparators.greatest(2, overloadComparator));
        InvokeInfoBuilder invoker = null;
        if (possibleInvokes.size() == 2) {
            if (overloadComparator.compare(possibleInvokes.get(0), possibleInvokes.get(1)) > 0)
                invoker = possibleInvokes.get(0);
            else if (overloadComparator.compare(possibleInvokes.get(0), possibleInvokes.get(1)) < 0)
                invoker = possibleInvokes.get(1);
        }
        if (possibleInvokes.size() == 1) {
            invoker = possibleInvokes.get(0);
        }
        if (invoker != null) {
            dataStore.promoteDataTargets(invoker.outputsId);
            return Optional.of(invoker.build());
        }
        return Optional.empty();
    }

    private Optional<InvokeInfoBuilder> resolve(MethodBindInfo method, BindingDataStore dataStore) {
        try {
            final InvokeInfoBuilder invokeInfo = new InvokeInfoBuilder(method);
            for (ParamBindInfo param : method.params) {
                Optional<BindingData> argument;
                if (OutputBinding.class.isAssignableFrom(TypeUtils.getRawType(param.type, null))) {
                    argument = dataStore.getOrAddDataTarget(invokeInfo.outputsId, param.name, param.type);
                } else if (param.name != null && !param.name.isEmpty()) {
                    argument = dataStore.getDataByName(param.name, param.type);
                } else {
                    argument = dataStore.getDataByType(param.type);
                }
                BindingData actualArg = argument.orElseThrow(WrongMethodTypeException::new);
                invokeInfo.matchingLevelCount[actualArg.getLevel().getIndex()]++;
                invokeInfo.appendArgument(actualArg.getValue());
            }
            if (!method.entry.getReturnType().equals(void.class) && !method.entry.getReturnType().equals(Void.class)) {
                dataStore.getOrAddDataTarget(invokeInfo.outputsId, BindingDataStore.RETURN_NAME, method.entry.getReturnType());
            }
            return Optional.of(invokeInfo);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
        InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.entry); }
        private final UUID outputsId = UUID.randomUUID();
        private final int[] matchingLevelCount = new int[BindingData.MatchingLevel.count()];
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
        }
        private final String name;
        private final Type type;
    }

    private final List<MethodBindInfo> candidates;
}
