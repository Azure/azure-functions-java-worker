package com.microsoft.azure.functions.worker.binding;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.TypedDataCollectionBytes;
import org.apache.commons.lang3.ArrayUtils;
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class RpcCollectionByteArrayDataSource extends DataSource<List<ByteString>> {
	public RpcCollectionByteArrayDataSource(String name, TypedDataCollectionBytes value) {
		super(name, value.getBytesList(), COLLECTION_DATA_OPERATIONS);
	}
	private static final DataOperations<List<ByteString>, Object> COLLECTION_DATA_OPERATIONS = new DataOperations<>();

	public static Object convertToByteList(List<ByteString> sourceValue, Type targetType) {
        Type targetActualType = ((ParameterizedTypeImpl) targetType).getActualTypeArguments()[0];
		if (targetActualType == byte[].class) {
			return sourceValue.stream().map(element -> element.toByteArray()).collect(Collectors.toCollection(ArrayList::new));
		} else if (targetActualType == Byte[].class) {
			return sourceValue.stream().map(element ->  (ArrayUtils.toObject(element.toByteArray()))).collect(Collectors.toList());
		}
		throw new UnsupportedOperationException("Input data type \"" + targetActualType + "\" is not supported");
	}

	public static Object convertToByteListDefault(List<ByteString> sourceValue, Type targetType) {
		return sourceValue.stream().map(element ->  (ArrayUtils.toObject(element.toByteArray()))).collect(Collectors.toList());
	}

	public static Object convertToBytesArray(List<ByteString> sourceValue, Type targetType) {
		return sourceValue.stream().map(element -> element.toByteArray()).collect(Collectors.toCollection(ArrayList::new)).toArray(new byte[0][]);
	}

	public static Object convertToBytesObjectArray(List<ByteString> sourceValue, Type targetType) {
		return sourceValue.stream().map(element ->  (ArrayUtils.toObject(element.toByteArray()))).collect(Collectors.toCollection(ArrayList::new)).toArray(new Byte[0][]);
	}

	static {
		COLLECTION_DATA_OPERATIONS.addGenericOperation(List.class, (v, t) -> convertToByteList(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(byte[][].class, (v, t) -> convertToBytesArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(Byte[][].class, (v, t) -> convertToBytesObjectArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(String.class, (v, t) -> convertToByteListDefault(v, t));
	}
}

