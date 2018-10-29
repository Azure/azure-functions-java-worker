package com.microsoft.azure.functions.worker.binding;

import java.util.*;

import com.google.protobuf.*;

import org.apache.commons.lang3.*;

final class RpcEmptyDataSource extends DataSource<Object> {
    RpcEmptyDataSource(String name) { super(name, null, EMPTY_DATA_OPERATIONS); }

    private static final DataOperations<Object, Object> EMPTY_DATA_OPERATIONS = new DataOperations<>();
    static {
        EMPTY_DATA_OPERATIONS.addOperation(Optional.class, v -> Optional.empty());
    }
}
