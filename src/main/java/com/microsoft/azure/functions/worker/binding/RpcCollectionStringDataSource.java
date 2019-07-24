package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.CollectionString;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RpcCollectionStringDataSource extends DataSource<List<String>> {
	public RpcCollectionStringDataSource(String name, CollectionString value) {
		super(name, value.getStringList(), COLLECTION_DATA_OPERATIONS);
	}
	private static final DataOperations<List<String>, Object> COLLECTION_DATA_OPERATIONS = new DataOperations<>();

	public static Object convertToStringList(List<String> sourceValue, Type targetType) {
		if(targetType == List.class) {
			return new ArrayList<>(sourceValue);
		}
		else if(targetType instanceof ParameterizedType) {
			Type targetActualType = ((ParameterizedType) targetType).getActualTypeArguments()[0];
			if (targetActualType == String.class) {
				return new ArrayList<>(sourceValue);
			}
			throw new UnsupportedOperationException("Input data type \"" + targetActualType + "\" is not supported");
		}
		throw new UnsupportedOperationException("Input data type \"" + targetType + "\" is not supported");
	}

	public static Object convertToStringArray(List<String> sourceValue, Type targetType) {
		return new ArrayList<>(sourceValue).toArray(new String[0]);
	}

	public static Object convertToStringListDefault(List<String> sourceValue, Type targetType) {
		return new ArrayList<>(sourceValue);
	}

	static {
		COLLECTION_DATA_OPERATIONS.addGenericOperation(List.class, (v, t) -> convertToStringList(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(String[].class, (v, t) -> convertToStringArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(String.class, (v, t) -> convertToStringListDefault(v, t));
	}
}

