package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.TypedDataCollectionSInt64;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RpcCollectionLongDataSource extends DataSource<List<Long>> {
	public RpcCollectionLongDataSource(String name, TypedDataCollectionSInt64 value) {
		super(name, value.getSint64List(), COLLECTION_DATA_OPERATIONS);
	}
	private static final DataOperations<List<Long>, Object> COLLECTION_DATA_OPERATIONS = new DataOperations<>();

	public static Object convertToLongList(List<Long> sourceValue, Type targetType) {
		if(targetType == List.class) {
			return new ArrayList<>(sourceValue);
		}
		else if(targetType instanceof ParameterizedTypeImpl) {
			Type targetActualType = ((ParameterizedTypeImpl) targetType).getActualTypeArguments()[0];
			if (targetActualType == Long.class) {
				return new ArrayList<>(sourceValue);
			}
			throw new UnsupportedOperationException("Input data type \"" + targetActualType + "\" is not supported");
		}
		throw new UnsupportedOperationException("Input data type \"" + targetType + "\" is not supported");
	}

	public static Object convertToLongListDefault(List<Long> sourceValue, Type targetType) {
		return new ArrayList<>(sourceValue);
	}

	public static Object convertToLongObjectArray(List<Long> sourceValue, Type targetType) {
		return new ArrayList<>(sourceValue).toArray(new Long[0]);
	}

	public static Object convertToLongArray(List<Long> sourceValue, Type targetType) {
		return sourceValue.stream().mapToLong(Long::longValue).toArray();
	}

	static {
		COLLECTION_DATA_OPERATIONS.addGenericOperation(List.class, (v, t) -> convertToLongList(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(Long[].class, (v, t) -> convertToLongObjectArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(long[].class, (v, t) -> convertToLongArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(String.class, (v, t) -> convertToLongListDefault(v, t));
	}
}

