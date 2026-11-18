package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.CollectionModelBindingData;

/**
 * Accepts a batch of model binding data so the invocation is not rejected. SDK types read the
 * raw envelopes through {@link WorkerSdkInvocationContext}, so no conversions are offered.
 */
public final class RpcCollectionModelBindingDataSource extends DataSource<CollectionModelBindingData> {
	public RpcCollectionModelBindingDataSource(String name, CollectionModelBindingData value) {
		super(name, value, new DataOperations<>());
	}
}
