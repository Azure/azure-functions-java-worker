package com.microsoft.azure.functions.worker.binding;

public final class RpcIntegerDataSource extends DataSource<Long> {
	public RpcIntegerDataSource(String name, long value) {
		super(name, value, LONG_DATA_OPERATIONS);
	}

	private static final DataOperations<Long, Object> LONG_DATA_OPERATIONS = new DataOperations<>();
	static {
		LONG_DATA_OPERATIONS.addOperation(int.class, Long::intValue);
		LONG_DATA_OPERATIONS.addOperation(Integer.class, Long::intValue);
		LONG_DATA_OPERATIONS.addOperation(short.class, Long::shortValue);
		LONG_DATA_OPERATIONS.addOperation(Short.class, Long::shortValue);
		LONG_DATA_OPERATIONS.addOperation(byte.class, Long::byteValue);
		LONG_DATA_OPERATIONS.addOperation(Byte.class, Long::byteValue);
		LONG_DATA_OPERATIONS.addOperation(String.class, Object::toString);
	}
}