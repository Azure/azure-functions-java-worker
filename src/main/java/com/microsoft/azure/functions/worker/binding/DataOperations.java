package com.microsoft.azure.functions.worker.binding;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.reflect.TypeUtils;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.functions.worker.broker.*;

@FunctionalInterface
interface CheckedFunction<T, R> {
	R apply(T t) throws Exception;

	default R tryApply(T t) {
		try {
			return this.apply(t);
		} catch (Exception ex) {
			// WorkerLogManager.getSystemLogger().warning(ExceptionUtils.getRootCauseMessage(ex));
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
class DataOperations<T, R> {
	DataOperations() {
		this.operations = new HashMap<>();
	}

	void addOperation(Type targetType, CheckedFunction<T, R> operation) {
		this.addFullOperation(targetType, (src, type) -> operation.apply(src));
	}

	void addFullOperation(Type targetType, CheckedBiFunction<T, Type, R> operation) {
		this.operations.put(targetType, operation);
	}

	Optional<R> apply(T sourceValue, Type targetType) throws JsonParseException, JsonMappingException, IOException {
		Optional<R> resultValue = null;

		if (sourceValue != null) {
			CheckedBiFunction<T, Type, R> matchingOperation = this.operations.get(TypeUtils.getRawType(targetType, null));
			if (matchingOperation != null) {
				resultValue = Optional.ofNullable(matchingOperation).map(op -> op.tryApply(sourceValue, targetType));
			}
			else
			{
				// Try POJO
				ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
				RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		        RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
		        RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		        RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);
		        if(Collection.class.isAssignableFrom(TypeUtils.getRawType(targetType, null)))
                {
		        	ParameterizedType pType = (ParameterizedType) targetType;
		        	Class<?> collectionItemType = (Class<?>) pType.getActualTypeArguments()[0];
		        	String sourceData = (String)sourceValue;
		            Object objList = RELAXED_JSON_MAPPER.readValue(sourceData, RELAXED_JSON_MAPPER.getTypeFactory().constructCollectionType(List.class, collectionItemType));
		            resultValue = (Optional<R>)Optional.ofNullable(objList);                    
                }
		        else
		        {
		        	Object obj = RELAXED_JSON_MAPPER.readValue((String)sourceValue, TypeUtils.getRawType(targetType, null));
			        resultValue = (Optional<R>)Optional.ofNullable(obj);
		        }		        
			}
		}

		if (resultValue == null || !resultValue.isPresent()) {
			resultValue = ((Optional<R>) Optional.ofNullable(generalAssignment(sourceValue, targetType)));
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
		throw new ClassCastException("Cannot convert " + value + "to type " + target.getTypeName());
	}

	private final Map<Type, CheckedBiFunction<T, Type, R>> operations;
}
