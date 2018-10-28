package com.microsoft.azure.functions.worker.binding;

import java.util.Map;
import java.util.Optional;

import com.microsoft.azure.functions.rpc.messages.TypedData;

final class RpcTriggerMetadataDataSource extends DataSource<Map<String, TypedData>> {
    RpcTriggerMetadataDataSource(Map<String, TypedData> metadata) {
        super(null, metadata, TRIGGER_METADATA_OPERATIONS);
    }

    @Override
    Optional<DataSource<?>> lookupName(String name) {
      return Optional.ofNullable(this.getValue().get(name)).map(v -> BindingDataStore.rpcSourceFromTypedData(name, v));
    }

    private static final DataOperations<Map<String, TypedData>, Object> TRIGGER_METADATA_OPERATIONS = new DataOperations<>();
}