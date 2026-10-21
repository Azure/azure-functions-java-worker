package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.CollectionModelBindingData;

/** Opaque SDK envelopes are consumed through the invocation context. */
public final class RpcCollectionModelBindingDataSource extends DataSource<CollectionModelBindingData> {
    public RpcCollectionModelBindingDataSource(String name, CollectionModelBindingData data) {
        super(name, data, new DataOperations<>());
    }
}
