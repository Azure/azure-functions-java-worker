package com.microsoft.azure.webjobs.script.binding;

import java.util.*;
import java.util.function.*;

class InputData extends BindingData {
    InputData(Value value) { this(null, value); }
    InputData(String name, Value value) { super(name, value); }

    void registerAssignment(Class<?> type, Supplier<Value> operation) {
        this.assignSuppliers.put(type, operation);
    }

    Optional<Value> assignTo(Class<?> target) {
        if (target.isAssignableFrom(this.getValue().getClass())) {
            return Optional.of(this.getValue());
        }
        Value assigned = this.assignedCache.get(target);
        if (assigned == null) {
            Supplier<Value> supplier = this.assignSuppliers.get(target);
            if (assignSuppliers != null) {
                assigned = supplier.get();
                this.assignedCache.put(target, assigned);
            }
        }
        return (assigned != null) ? Optional.of(assigned) : Optional.empty();
    }

    Optional<Value> convertTo(Class<?> target) {
        return this.assignTo(target);
    }

    private Set<String> additionalNames = new HashSet<>();
    private Map<Class<?>, Value> assignedCache = new HashMap<>();
    private Map<Class<?>, Supplier<Value>> assignSuppliers = new HashMap<>();
}
