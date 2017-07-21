package com.microsoft.azure.webjobs.script.binding;

import java.util.*;

public class InputData extends BindingData {
    public InputData(Value value) { this(null, value); }
    public InputData(String name, Value value) { super(name, value); }

    public Optional<Value> assignTo(Class<?> target) {
        if (target.isAssignableFrom(this.getValue().getClass())) {
            return Optional.of(this.getValue());
        }
        return Optional.empty();
    }

    public Optional<Value> convertTo(Class<?> target) {
        return this.assignTo(target);
    }
}
