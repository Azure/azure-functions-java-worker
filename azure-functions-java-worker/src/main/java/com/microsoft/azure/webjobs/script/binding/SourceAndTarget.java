package com.microsoft.azure.webjobs.script.binding;

import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.reflect.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.binding.BindingData.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

import static com.microsoft.azure.webjobs.script.binding.BindingData.MatchingLevel.*;

/**
 * Base class of all data sources. Provides basic information and logic for type conversion.
 * Data operation template: T (source) -> Object (value).
 * Thread-safety: Single thread.
 */
abstract class DataSource<T> {
    DataSource(String name, T value, DataOperations<T, Object> operations) {
        this.name = name;
        this.value = value;
        this.operations = operations;
    }

    T getValue() { return this.value; }
    void setValue(T value) { this.value = value; }

    Optional<BindingData> computeByName(MatchingLevel level, String name, Type target) {
        Optional<DataSource<?>> source = this.lookupName(level, name);
        if (!source.isPresent()) {
            if (target.equals(Optional.class)) {
                return Optional.of(new BindingData(Optional.empty(), level));
            }
            return Optional.empty();
        }
        Optional<BindingData> data = source.get().computeByType(target);
        data.ifPresent(d -> d.setLevel(level));
        return data;
    }

    Optional<BindingData> computeByType(MatchingLevel level, Type target) {
        boolean isTargetOptional = Optional.class.equals(TypeUtils.getRawType(target, null));
        if (isTargetOptional) {
            Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(target, Optional.class);
            target = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
        }
        return this.operations.apply(this.value, level, target).map(obj -> {
            if (isTargetOptional) {
                if (obj == ObjectUtils.NULL) {
                    obj = null;
                }
                obj = Optional.ofNullable(obj);
            }
            return new BindingData(obj, level);
        });
    }

    Optional<DataSource<?>> lookupName(MatchingLevel level, String name) {
        return Optional.ofNullable(level == BINDING_NAME && this.name != null && this.name.equals(name) ? this : null);
    }

    Optional<BindingData> computeByType(Type target) {
        for (MatchingLevel level : Arrays.asList(TYPE_ASSIGNMENT, TYPE_STRICT_CONVERSION, TYPE_RELAXED_CONVERSION)) {
            Optional<BindingData> data = this.computeByType(level, target);
            if (data.isPresent()) { return data; }
        }
        return Optional.empty();
    }

    private final String name;
    private T value;
    private final DataOperations<T, Object> operations;
}

/**
 * Base class of all output data sources. The type conversion logic is just the opposite of the normal input data source.
 * Data operation template: Object (source) -> TypedData.Builder.
 * Thread-safety: Single thread.
 */
abstract class DataTarget implements OutputBinding {
    DataTarget(DataOperations<Object, TypedData.Builder> operations) {
        this.operations = operations;
    }

    Optional<TypedData> computeFromValue() {
        return this.computeFromValueByLevels(TYPE_ASSIGNMENT, TYPE_STRICT_CONVERSION, TYPE_RELAXED_CONVERSION);
    }

    private Optional<TypedData> computeFromValueByLevels(MatchingLevel... levels) {
        if (this.value == null) {
            return Optional.of(TypedData.newBuilder().setJson("null").build());
        }
        for (MatchingLevel level : levels) {
            Optional<TypedData> data = this.operations.apply(this.value, level, this.value.getClass()).map(TypedData.Builder::build);
            if (data.isPresent()) { return data; }
        }
        return Optional.empty();
    }

    @Override
    public Object getValue() { return this.value; }

    @Override
    public void setValue(Object value) { this.value = value; }

    private Object value;
    private final DataOperations<Object, TypedData.Builder> operations;
}
