package com.microsoft.azure.webjobs.script.binding;

import java.util.*;
import java.util.function.*;

class InputData<T> extends BindingData<T> {
    InputData(Value<T> value) { this(null, value); }
    InputData(String name, Value<T> value) { super(name, value); }

    <V> void registerAssignment(Class<V> toType, Supplier<V> operation) {
        this.assignSuppliers.put(toType, () -> new Value<>(operation.get()));
    }

    void setOrElseAssignment(DataTransformSupplier<Class<?>, Object> operation) {
        this.orElseAssignSupplier = arg -> operation.get(arg).map(Value::new);
    }

    <V> void registerConversion(Class<V> toType, Supplier<V> operation) {
        this.convertSuppliers.put(toType, () -> new Value<>(operation.get()));
    }

    void setOrElseConversion(DataTransformSupplier<Class<?>, Object> operation) {
        this.orElseConvertSupplier = arg -> operation.get(arg).map(Value::new);
    }

    Optional<Value<?>> assignTo(Class<?> target) {
        if (target.isAssignableFrom(this.getValue().getActual().getClass())) {
            return Optional.of(this.getValue());
        }
        return this.transformTo(target, this.assignedCache, this.assignSuppliers, this.orElseAssignSupplier);
    }

    Optional<Value<?>> convertTo(Class<?> target) {
        Optional<Value<?>> converted = this.assignTo(target);
        if (converted.isPresent()) { return converted; }
        return this.transformTo(target, this.convertedCache, this.convertSuppliers, this.orElseConvertSupplier);
    }

    private Optional<Value<?>> transformTo(Class<?> target,
                                           Map<Class<?>, Value<?>> cache,
                                           Map<Class<?>, Supplier<Value<?>>> suppliers,
                                           DataTransformSupplier<Class<?>, Value<?>> orElseSupplier) {
        Value<?> transformed = cache.get(target);
        if (transformed == null) {
            Supplier<Value<?>> supplier = suppliers.get(target);
            if (supplier != null) {
                transformed = supplier.get();
            } else if (orElseSupplier != null) {
                try { transformed = orElseSupplier.get(target).orElse(null); }
                catch (Exception ex) { transformed = null; }
            }
        }
        if (transformed != null) { cache.put(target, transformed); }
        return Optional.ofNullable(transformed);
    }

    @FunctionalInterface
    interface DataTransformSupplier<U, V> { Optional<V> get(U arg); }

    private Set<String> additionalNames = new HashSet<>();
    private Map<Class<?>, Value<?>> assignedCache = new HashMap<>();
    private Map<Class<?>, Value<?>> convertedCache = new HashMap<>();
    private Map<Class<?>, Supplier<Value<?>>> assignSuppliers = new HashMap<>();
    private Map<Class<?>, Supplier<Value<?>>> convertSuppliers = new HashMap<>();
    private DataTransformSupplier<Class<?>, Value<?>> orElseAssignSupplier = null;
    private DataTransformSupplier<Class<?>, Value<?>> orElseConvertSupplier = null;
}
