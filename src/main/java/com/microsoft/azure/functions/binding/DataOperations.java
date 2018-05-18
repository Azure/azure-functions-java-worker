package com.microsoft.azure.functions.binding;

import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.reflect.*;

import com.microsoft.azure.functions.binding.BindingData.*;
import com.microsoft.azure.functions.broker.*;

@FunctionalInterface
interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;

    default R tryApply(T t) {
        try { return this.apply(t); }
        catch (Exception ex) { return null; }
    }
}

@FunctionalInterface
interface CheckedBiFunction<T, U, R> {
    R apply(T t, U u) throws Exception;

    default R tryApply(T t, U u) {
        try { return this.apply(t, u); }
        catch (Exception ex) { return null; }
    }
}

/**
 * Helper class to define data conversion operations.
 * Thread-safety: Single thread.
 * @param <T> Type of the source data.
 * @param <R> Type of the target data.
 */
class DataOperations<T, R> {
    DataOperations() {
        this.operations = new HashMap<>();
        this.guardOperations = new HashMap<>();
    }

    void addOperation(MatchingLevel level, Type targetType, CheckedFunction<T, R> operation) {
        this.addOperation(level, targetType, (src, type) -> operation.apply(src));
    }

    private void addOperation(MatchingLevel level, Type targetType, CheckedBiFunction<T, Type, R> operation) {
        this.operations.computeIfAbsent(level, l -> new HashMap<>()).put(targetType, operation);
    }

    void addGuardOperation(MatchingLevel level, CheckedBiFunction<T, Type, R> operation) {
        this.guardOperations.put(level, operation);
    }

    Optional<R> apply(T sourceValue, MatchingLevel level, Type targetType) {
        Optional<R> resultValue = Optional.ofNullable(this.operations.get(level))
            .map(opMap -> opMap.get(TypeUtils.getRawType(targetType, null)))
            .map(op -> op.tryApply(sourceValue, targetType));
        if (!resultValue.isPresent()) {
            resultValue = Optional.ofNullable(this.guardOperations.get(level)).map(op -> op.tryApply(sourceValue, targetType));
        }
        return resultValue;
    }

    static Object generalAssignment(Object value, Type target) {
        if (value == null) {
            return ObjectUtils.NULL;
        }
        if (CoreTypeResolver.getRuntimeClass(target).isAssignableFrom(value.getClass())) {
            return value;
        }
        throw new ClassCastException();
    }

    private final Map<MatchingLevel, Map<Type, CheckedBiFunction<T, Type, R>>> operations;
    private final Map<MatchingLevel, CheckedBiFunction<T, Type, R>> guardOperations;
}
