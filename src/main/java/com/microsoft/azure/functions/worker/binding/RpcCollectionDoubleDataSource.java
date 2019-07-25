package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.CollectionDouble;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class RpcCollectionDoubleDataSource extends DataSource<List<Double>> {
	public RpcCollectionDoubleDataSource(String name, CollectionDouble value) {
		super(name, value.getDoubleList(), COLLECTION_DATA_OPERATIONS);
	}
	private static final DataOperations<List<Double>, Object> COLLECTION_DATA_OPERATIONS = new DataOperations<>();

	public static Object convertToDoubleListDefault(List<Double> sourceValue, Type targetType) {
		return new ArrayList<>(sourceValue);
	}

	public static Object convertToDoubleList(List<Double> sourceValue, Type targetType) {
		if(targetType == List.class) {
			return new ArrayList<>(sourceValue);
		}
		else if(targetType instanceof ParameterizedType) {
			Type targetActualType = ((ParameterizedType) targetType).getActualTypeArguments()[0];
			if (targetActualType == Double.class) {
				return new ArrayList<>(sourceValue);
			}
			throw new UnsupportedOperationException("Input data type \"" + targetActualType + "\" is not supported");
		}
		throw new UnsupportedOperationException("Input data type \"" + targetType + "\" is not supported");
	}

    public static Object convertToDoubleObjectArray(List<Double> sourceValue, Type targetType) {
        return new ArrayList<>(sourceValue).toArray(new Double[0]);
    }

    public static Object convertToDoubleArray(List<Double> sourceValue, Type targetType) {
        return sourceValue.stream().mapToDouble(Double::doubleValue).toArray();
    }

	static {
		COLLECTION_DATA_OPERATIONS.addGenericOperation(List.class, (v, t) -> convertToDoubleList(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(Double[].class, (v, t) -> convertToDoubleObjectArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(double[].class, (v, t) -> convertToDoubleArray(v, t));
		COLLECTION_DATA_OPERATIONS.addGenericOperation(String.class, (v, t) -> convertToDoubleListDefault(v, t));
    }
}

