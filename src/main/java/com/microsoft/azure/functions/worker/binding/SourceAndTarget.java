package com.microsoft.azure.functions.worker.binding;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.reflect.*;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.rpc.messages.*;

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

	Optional<BindingData> computeByName(String name, Type target) {
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
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return Optional.empty();
	}

	Optional<BindingData> computeByNameList(String name, Type target)
			throws JsonParseException, JsonMappingException, IOException {
		Optional<DataSource<?>> source = this.lookupName(name);
		if (!source.isPresent()) {
			if (target.equals(Optional.class)) {
				return Optional.of(new BindingData(Optional.empty()));
			}
			return Optional.empty();
		}
		Optional<BindingData> data = source.get().computeByList(target);
		return data;
	}

	Optional<BindingData> computeByType(Type target) throws JsonParseException, JsonMappingException, IOException {
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

	Optional<BindingData> computeByList(Type target) throws JsonParseException, JsonMappingException, IOException {
		boolean isTargetOptional = Optional.class.equals(TypeUtils.getRawType(target, null));
		if (isTargetOptional) {
			Map<TypeVariable<?>, Type> typeArgs = TypeUtils.getTypeArguments(target, Optional.class);
			target = typeArgs.size() > 0 ? typeArgs.values().iterator().next() : Object.class;
		}

		ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
		RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
		RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
		String sourceValue = (String) this.value;
		Object objList = RELAXED_JSON_MAPPER.readValue(sourceValue, RELAXED_JSON_MAPPER.getTypeFactory()
				.constructCollectionType(List.class, TypeUtils.getRawType(target, null)));
		return this.operations.apply(this.value, target).map(obj -> {
			obj = objList;
			if (isTargetOptional) {
				if (obj == ObjectUtils.NULL) {
					obj = null;
				}
				obj = Optional.ofNullable(objList);
			}
			return new BindingData(objList);
		});

	}

	Optional<DataSource<?>> lookupName(String name) {
		return Optional.ofNullable(this.name != null && this.name.equals(name) ? this : null);
	}

	private final String name;
	private T value;
	private final DataOperations<T, Object> operations;
}

/**
 * Base class of all output data sources. The type conversion logic is just the
 * opposite of the normal input data source. Data operation template: Object
 * (source) -> TypedData.Builder. Thread-safety: Single thread.
 */
abstract class DataTarget implements OutputBinding {
	DataTarget(DataOperations<Object, TypedData.Builder> operations) {
		this.operations = operations;
	}

	Optional<TypedData> computeFromValue() {
		return this.computeFromValueByLevels();
	}

	private Optional<TypedData> computeFromValueByLevels() {
		if (this.value == null) {
			return Optional.of(TypedData.newBuilder().setJson("null").build());
		}

		Optional<TypedData> data;
		try {
			data = this.operations.applyTypeAssignment(this.value, this.value.getClass())
					.map(TypedData.Builder::build);
			if (data.isPresent()) {
				return data;
			}		
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return Optional.empty();
	}

	@Override
	public Object getValue() {
		return this.value;
	}

	@Override
	public void setValue(Object value) {
		this.value = value;
	}

	private Object value;
	private final DataOperations<Object, TypedData.Builder> operations;
}
