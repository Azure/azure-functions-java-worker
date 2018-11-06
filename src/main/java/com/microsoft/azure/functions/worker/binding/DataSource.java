package com.microsoft.azure.functions.worker.binding;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

/**
 * Base class of all data sources. Provides basic information and logic for type
 * conversion. Data operation template: T (source) -> Object (value).
 * Thread-safety: Single thread.
 */
abstract class DataSource<T> {
	DataSource(String name, T value, DataOperations<T, Object> operations) {
		this.name = name;
		this.value = value;
		this.operations = operations;
	}

	T getValue() {
		return this.value;
	}

	void setValue(T value) {
		this.value = value;
	}

	public Optional<BindingData> computeByName(String name, Type target) {
		Optional<DataSource<?>> source = this.lookupName(name);
		if (!source.isPresent()) {
			if (target.equals(Optional.class)) {
				return Optional.of(new BindingData(Optional.empty()));
			}
			return Optional.empty();
		}
		Optional<BindingData> data;
		try {
			data = source.get().computeByType(target);
			return data;
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
		}
		return Optional.empty();
	}

	public Optional<BindingData> computeByType(Type target) {
		boolean isTargetOptional = Optional.class.equals(TypeUtils.getRawType(target, null));
		if (isTargetOptional) {
			Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(target, Optional.class);
			target = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
		}
		return this.operations.apply(this.value, target).map(obj -> {
			if (isTargetOptional) {
				if (obj == ObjectUtils.NULL) {
					obj = null;
				}
				obj = Optional.ofNullable(obj);
			}
			return new BindingData(obj);
		});
	}

	protected Optional<DataSource<?>> lookupName(String name) {
		return Optional.ofNullable(this.name != null && this.name.equals(name) ? this : null);
	}

	private final String name;
	private T value;
	private final DataOperations<T, Object> operations;
	
}
