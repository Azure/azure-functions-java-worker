package com.microsoft.azure.functions.worker.binding;

import org.apache.commons.lang3.ArrayUtils;

import com.google.protobuf.ByteString;

final public class RpcByteArrayDataSource extends DataSource<byte[]> {
  public RpcByteArrayDataSource(String name, ByteString value) {
    super(name, value.toByteArray(), BYTE_ARRAY_DATA_OPERATIONS);
  }

  private static final DataOperations<byte[], Object> BYTE_ARRAY_DATA_OPERATIONS = new DataOperations<>();
  static {
    BYTE_ARRAY_DATA_OPERATIONS.addOperation(Byte[].class, ArrayUtils::toObject);
    BYTE_ARRAY_DATA_OPERATIONS.addOperation(byte[].class, ArrayUtils::clone);
  }
}