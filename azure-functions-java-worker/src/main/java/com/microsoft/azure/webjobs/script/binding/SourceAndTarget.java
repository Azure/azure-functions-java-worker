package com.microsoft.azure.webjobs.script.binding;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.binding.BindingData.*;
import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;

/**
 * Base class of all data sources. Provides basic information and logic for type conversion.
 * Data operation template: T (source) -> Object (value).
 * Thread-safety: Single thread.
 */
abstract class DataSource<T> {
    DataSource(String name, T value, DataOperations<T> operations) {
        this.name = name;
        this.value = value;
        this.operations = operations;
    }

    T getValue() { return this.value; }
    void setValue(T value) { this.value = value; }

    Optional<BindingData> computeByName(MatchingLevel level, String name, Type target) {
        Optional<DataSource<?>> source = this.lookupName(level, name);
        if (!source.isPresent()) { return Optional.empty(); }
        Optional<BindingData> data = source.get().computeByType(target);
        data.ifPresent(d -> d.setLevel(level));
        return data;
    }

    Optional<BindingData> computeByType(MatchingLevel level, Type target) {
        try {
            Optional<BindingData> binding = this.operations.getOperation(level, target).map(op -> new BindingData(op.apply(this.value), level));
            if (!binding.isPresent()) {
                binding = this.operations.getGuardOperation(level).map(op -> new BindingData(op.apply(this.value, target), level));
            }
            return binding;
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private Optional<DataSource<?>> lookupName(MatchingLevel level, String name) {
        return Optional.ofNullable(level == BINDING_NAME && this.name != null && this.name.equals(name) ? this : null);
    }

    private Optional<BindingData> computeByType(Type target) {
        Optional<BindingData> data = this.computeByType(TYPE_ASSIGNMENT, target);
        if (data.isPresent()) { return data; }
        data = this.computeByType(TYPE_STRICT_CONVERSION, target);
        if (data.isPresent()) { return data; }
        return this.computeByType(TYPE_RELAXED_CONVERSION, target);
    }

    private final String name;
    private T value;
    private final DataOperations<T> operations;
}

/**
 * Base class of all output data sources. The type conversion logic is just the opposite of the normal input data source.
 * Data operation template: Object (source) -> TypedData.Builder.
 * Thread-safety: Single thread.
 */
abstract class DataTarget implements OutputParameter {
    DataTarget(DataOperations<Object> operations) {
        this.operations = operations;
    }

    Optional<TypedData> computeFromValue() {
        return this.computeFromValueByLevels(TYPE_ASSIGNMENT, TYPE_STRICT_CONVERSION, TYPE_RELAXED_CONVERSION);
    }

    private Optional<TypedData> computeFromValueByLevels(MatchingLevel... levels) {
        Type source = this.getValue().getClass();
        for (MatchingLevel level : levels) {
            Optional<?> dataBuilder = this.operations.getOperation(level, source).map(op -> op.apply(this.getValue()));
            if (!dataBuilder.isPresent()) {
                dataBuilder = this.operations.getGuardOperation(level).map(op -> op.apply(this.getValue(), source));
            }
            if (dataBuilder.isPresent()) {
                assert dataBuilder.get() instanceof TypedData.Builder;
                return Optional.of(((TypedData.Builder)dataBuilder.get()).build());
            }
        }
        return Optional.empty();
    }

    @Override
    public Object getValue() { return this.value; }

    @Override
    public void setValue(Object value) { this.value = value; }

    private Object value;
    private final DataOperations<Object> operations;
}

/**
 * Helper class to define data conversion operations.
 * Thread-safety: Single thread.
 * @param <T> Type of the source data.
 */
final class DataOperations<T> {
    DataOperations() {
        this.operations = new HashMap<>();
        this.guardOperations = new HashMap<>();
    }

    void addOperation(MatchingLevel level, Type target, Function<T, ?> operation) {
        this.operations.computeIfAbsent(level, l -> new HashMap<>()).put(target, operation);
    }

    void addGuardOperation(MatchingLevel level, BiFunction<T, Type, ?> operation) {
        this.guardOperations.put(level, operation);
    }

    Optional<Function<T, ?>> getOperation(MatchingLevel level, Type target) {
        Map<Type, Function<T, ?>> targetTypeMap = this.operations.get(level);
        if (targetTypeMap == null) { return Optional.empty(); }
        return Optional.ofNullable(targetTypeMap.get(target));
    }

    Optional<BiFunction<T, Type, ?>> getGuardOperation(MatchingLevel level) {
        return Optional.ofNullable(this.guardOperations.get(level));
    }

    static Object generalAssignment(Object value, Type target) {
        if (value == null || CoreTypeResolver.getRuntimeClass(target).isAssignableFrom(value.getClass())) {
            return value;
        }
        throw new ClassCastException();
    }

    private final Map<MatchingLevel, Map<Type, Function<T, ?>>> operations;
    private final Map<MatchingLevel, BiFunction<T, Type, ?>> guardOperations;
}