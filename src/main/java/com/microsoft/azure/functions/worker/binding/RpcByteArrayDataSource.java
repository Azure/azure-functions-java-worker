package com.microsoft.azure.functions.worker.binding;

import org.apache.commons.lang3.ArrayUtils;

import com.google.protobuf.ByteString;

final class RpcByteArrayDataSource extends DataSource<byte[]> {
    RpcByteArrayDataSource(String name, ByteString value) { super(name, value.toByteArray(), BYTE_ARRAY_DATA_OPERATIONS); }

    private static final DataOperations<byte[], Object> BYTE_ARRAY_DATA_OPERATIONS = new DataOperations<>();
    static {
        BYTE_ARRAY_DATA_OPERATIONS.addOperation(Byte[].class, ArrayUtils::toObject);        
    }
}