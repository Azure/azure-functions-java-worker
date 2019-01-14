package com.microsoft.azure.functions.worker.binding;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.*;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.CoreTypeResolver;

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
			WorkerLogManager.getSystemLogger().warning(ExceptionUtils.getRootCauseMessage(ex));
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

	Optional<R> apply(T sourceValue, Type targetType) {
		Optional<R> resultValue = null;

		if (sourceValue != null) {
			CheckedBiFunction<T, Type, R> matchingOperation = this.operations
					.get(TypeUtils.getRawType(targetType, null));
			if (matchingOperation != null) {
				resultValue = Optional.ofNullable(matchingOperation).map(op -> op.tryApply(sourceValue, targetType));
			} else {		
			  String sourceData;
			  Class<?> sourceValueClass = sourceValue.getClass();
			  if (sourceValueClass.isAssignableFrom(byte[].class)) {
			    sourceData = Base64.getEncoder().encodeToString((byte[])sourceValue);			      
			  }
			  else
			  {
			    sourceData = (String) sourceValue;
			  }
			  
				// Try POJO
				if (Collection.class.isAssignableFrom(TypeUtils.getRawType(targetType, null))) {
					Class<?> collectionItemType = (Class<?>) CoreTypeResolver
							.getParameterizedActualTypeArgumentsType(targetType);

					try {
						Object objList = toList(sourceData, collectionItemType);
						resultValue = (Optional<R>) Optional.ofNullable(objList);
					} catch (Exception jsonParseEx) {
						resultValue = convertFromJson(sourceData, targetType);
					}
				} else {
					resultValue = convertFromJson(sourceData, TypeUtils.getRawType(targetType, null));
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

	private Optional<R> convertFromJson(String sourceValue, Type targetType) {
		if (null == sourceValue) {
			return null;
		}
		Object result = null;
		try {
			result = RpcJsonDataSource.gson.fromJson(sourceValue, targetType);
		} catch (JsonSyntaxException ex) {
			if (Collection.class.isAssignableFrom(TypeUtils.getRawType(targetType, null)) || targetType.getClass().isArray()) {
				result = RpcJsonDataSource.convertToStringArrayOrList(sourceValue, targetType);
			}
			else {
				throw ex;
			}
		}
		return (Optional<R>) Optional.ofNullable(result);
	}

	public static <T> List<T> toList(String json, Class<T> elementType) {
		if (null == json) {
			return null;
		}
		List<T> pojoList = new ArrayList<T>();
		JsonParser parser = new JsonParser();
		JsonArray array = parser.parse(json).getAsJsonArray();
		for (int i = 0; i < array.size(); i++) {
			pojoList.add((T) RpcJsonDataSource.gson.fromJson(array.get(i), elementType));
		}

		return pojoList;
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
