package com.microsoft.azure.functions.worker.binding;

import java.util.Optional;

import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.rpc.messages.TypedData.Builder;

/**
 * Base class of all output data sources. The type conversion logic is just the
 * opposite of the normal input data source. Data operation template: Object
 * (source) -> TypedData.Builder. Thread-safety: Single thread.
 */
abstract class DataTarget implements OutputBinding {
	DataTarget(DataOperations<Object, TypedData.Builder> dataOperations) {
		this.dataOperations = dataOperations;
	}

	Optional<TypedData> computeFromValue() throws Exception {
		if (this.value == null) {
			return Optional.of(TypedData.newBuilder().setJson("null").build());
		}

		Optional<TypedData> data;

		data = this.dataOperations.applyTypeAssignment(this.value, this.value.getClass()).map(TypedData.Builder::build);
		if (data.isPresent()) {
			return data;
		}

		return Optional.empty();
	}

	@Override
	public Object getValue() {
		return this.value;
	}

	@Override
	public void setValue(Object value) {
		this.value = value;
	}

	private Object value;
	private DataOperations<Object, TypedData.Builder> dataOperations;
}