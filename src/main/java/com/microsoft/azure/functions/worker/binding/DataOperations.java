package com.microsoft.azure.functions.worker.binding;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.DataTarget.*;
import com.microsoft.azure.functions.worker.broker.*;

@FunctionalInterface
interface CheckedFunction<T, R> {
	R apply(T t) throws Exception;

	default R tryApply(T t) {
		try {
			return this.apply(t);
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
			return null;
		}
	}
}

@FunctionalInterface
interface CheckedBiFunction<T, U, R> {
	R apply(T t, U u) throws Exception;

	default R tryApply(T t, U u) {
		try {
			return this.apply(t, u);
		} catch (Exception ex) {
			// WorkerLogManager.getSystemLogger().warning(ExceptionUtils.getRootCauseMessage(ex));
			return null;
		}
	}
}

/**
 * Helper class to define data conversion operations. Thread-safety: Single
 * thread.
 * 
 * @param <T> Type of the source data.
 * @param <R> Type of the target data.
 */
public class DataOperations<T, R> {
	DataOperations() {
		this.operations = new HashMap<>();
		this.targetOperations = new HashMap<>();
	}

	public void addTargetOperation(Type targetType, CheckedFunction<T, R> operation) {
		this.addGenericTargetOperation(targetType, (src, type) -> operation.apply(src));
	}

	public void addOperation(Type targetType, CheckedFunction<T, R> operation) {
		this.addGenericOperation(targetType, (src, type) -> operation.apply(src));
	}

	public void addGenericOperation(Type targetType, CheckedBiFunction<T, Type, R> operation) {
		this.operations.put(targetType, operation);
	}

	public void addGenericTargetOperation(Type targetType, CheckedBiFunction<T, Type, R> operation) {
		this.targetOperations.put(targetType, operation);
	}

	Optional<R> apply(T sourceValue, Type targetType) throws JsonParseException, JsonMappingException, IOException {
		Optional<R> resultValue = null;

		if (sourceValue != null) {
			CheckedBiFunction<T, Type, R> matchingOperation = this.operations
					.get(TypeUtils.getRawType(targetType, null));
			if (matchingOperation != null) {
				resultValue = Optional.ofNullable(matchingOperation).map(op -> op.tryApply(sourceValue, targetType));
			} else {
				// Try POJO
				ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
				RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
				RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
				RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
				RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
				if (Collection.class.isAssignableFrom(TypeUtils.getRawType(targetType, null))) {
					ParameterizedType pType = (ParameterizedType) targetType;
					Class<?> collectionItemType = (Class<?>) pType.getActualTypeArguments()[0];
					String sourceData = (String) sourceValue;
					try {
						Object objList = RELAXED_JSON_MAPPER.readValue(sourceData, RELAXED_JSON_MAPPER.getTypeFactory()
								.constructCollectionType(List.class, collectionItemType));
						resultValue = (Optional<R>) Optional.ofNullable(objList);
					} catch (Exception jsonParseEx) {
						resultValue = convertFromJson(sourceValue, targetType, RELAXED_JSON_MAPPER);
					}
				} else {
					resultValue = convertFromJson(sourceValue, TypeUtils.getRawType(targetType, null),
							RELAXED_JSON_MAPPER);
				}
			}
		}

		if (resultValue == null || !resultValue.isPresent()) {
			resultValue = ((Optional<R>) Optional.ofNullable(generalAssignment(sourceValue, targetType)));
		}
		return resultValue;
	}

	Optional<R> applyTypeAssignment(T sourceValue, Type targetType) throws Exception {
		Optional<R> resultValue = null;

		if (sourceValue != null) {
			CheckedBiFunction<T, Type, R> matchingOperation = this.targetOperations
					.get(TypeUtils.getRawType(targetType, null));
			if (matchingOperation != null) {
				resultValue = Optional.ofNullable(matchingOperation).map(op -> op.tryApply(sourceValue, targetType));
			} else {
				try {
					Object jsonResult = RpcUnspecifiedDataTarget.toJsonData(sourceValue);
					resultValue = (Optional<R>) Optional.ofNullable(jsonResult);

				} catch (Exception ex) {
					WorkerLogManager.getSystemLogger().warning(ExceptionUtils.getRootCauseMessage(ex));
					Object stringResult = RpcUnspecifiedDataTarget.toJsonData(sourceValue);
					resultValue = (Optional<R>) Optional.ofNullable(stringResult);
				}
			}
		}

		if (resultValue == null || !resultValue.isPresent()) {
			resultValue = ((Optional<R>) Optional.ofNullable(generalAssignment(sourceValue, targetType)));
		}
		return resultValue;
	}

	private Optional<R> convertFromJson(T sourceValue, Type targetType, ObjectMapper RELAXED_JSON_MAPPER)
			throws IOException, JsonParseException, JsonMappingException {
		Object obj = RELAXED_JSON_MAPPER.readValue((String) sourceValue, TypeUtils.getRawType(targetType, null));
		return (Optional<R>) Optional.ofNullable(obj);
	}

	static Object generalAssignment(Object value, Type target) {
		if (value == null) {
			return ObjectUtils.NULL;
		}
		if (CoreTypeResolver.getRuntimeClass(target).isAssignableFrom(value.getClass())) {
			return value;
		}
		throw new ClassCastException("Cannot convert " + value + "to type " + target.getTypeName());
	}

	private Map<Type, CheckedBiFunction<T, Type, R>> operations;
	private Map<Type, CheckedBiFunction<T, Type, R>> targetOperations;
}
