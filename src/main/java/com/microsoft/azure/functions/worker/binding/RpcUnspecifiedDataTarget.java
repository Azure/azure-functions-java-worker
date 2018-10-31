package com.microsoft.azure.functions.worker.binding;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.TypedData;

public final class RpcUnspecifiedDataTarget extends DataTarget {
	public RpcUnspecifiedDataTarget() {
		super(UNSPECIFIED_TARGET_OPERATIONS);
	}

	public static TypedData.Builder toStringData(Object value) {
		TypedData.Builder dataBuilder = TypedData.newBuilder();
		if (value != null) {
			dataBuilder.setString(value.toString());
		} else {
			throw new ClassCastException("Cannot convert " + value + "to String");
		}
		return dataBuilder;
	}

	private static TypedData.Builder toIntData(Object value) {
		TypedData.Builder dataBuilder = TypedData.newBuilder();
		if (value != null) {
			if (value instanceof Long) {
				dataBuilder.setInt((Long) value);
			} else if (value instanceof Integer) {
				dataBuilder.setInt((Integer) value);
			} else if (value instanceof Short) {
				dataBuilder.setInt((Short) value);
			} else if (value instanceof Byte) {
				dataBuilder.setInt((Byte) value);
			} else {
				throw new ClassCastException("Cannot convert " + value + "to Int");
			}
		}
		return dataBuilder;
	}

	private static TypedData.Builder toRealNumberData(Object value) {
		TypedData.Builder dataBuilder = TypedData.newBuilder();
		if (value != null) {
			if (value instanceof Double) {
				dataBuilder.setDouble((Double) value);
			} else if (value instanceof Float) {
				dataBuilder.setDouble((Float) value);
			} else {
				throw new ClassCastException("Cannot convert " + value + "to Double");
			}
		}
		return dataBuilder;
	}

	private static TypedData.Builder toByteArrayData(Object value) {
		TypedData.Builder dataBuilder = TypedData.newBuilder();
		if (value != null) {
			if (value instanceof byte[]) {
				dataBuilder.setBytes(ByteString.copyFrom((byte[]) value));
			} else {
				throw new ClassCastException("Cannot convert " + value + "to ByteString");
			}
		}
		return dataBuilder;
	}

	public static TypedData.Builder toJsonData(Object value) throws Exception {
		TypedData.Builder dataBuilder = TypedData.newBuilder();
		if (value != null) {
			dataBuilder.setJson(RELAXED_JSON_MAPPER.writeValueAsString(value));
		} else {
			throw new IllegalArgumentException();
		}
		return dataBuilder;
	}

	private static final ObjectMapper RELAXED_JSON_MAPPER = new ObjectMapper();
	private static final DataOperations<Object, TypedData.Builder> UNSPECIFIED_TARGET_OPERATIONS = new DataOperations<>();
	static {
		RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
		RELAXED_JSON_MAPPER.setVisibility(PropertyAccessor.CREATOR, JsonAutoDetect.Visibility.ANY);
		RELAXED_JSON_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		RELAXED_JSON_MAPPER.enable(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES);

		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(long.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Long.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(int.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Integer.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(short.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Short.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(byte.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Byte.class, RpcUnspecifiedDataTarget::toIntData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(double.class, RpcUnspecifiedDataTarget::toRealNumberData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Double.class, RpcUnspecifiedDataTarget::toRealNumberData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(float.class, RpcUnspecifiedDataTarget::toRealNumberData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(Float.class, RpcUnspecifiedDataTarget::toRealNumberData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(byte[].class, RpcUnspecifiedDataTarget::toByteArrayData);
		UNSPECIFIED_TARGET_OPERATIONS.addTargetOperation(String.class, RpcUnspecifiedDataTarget::toStringData);
	}
}
