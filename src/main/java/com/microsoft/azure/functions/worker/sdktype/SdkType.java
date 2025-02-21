package com.microsoft.azure.functions.worker.sdktype;

import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;

/**
 * Generic interface/class for each SDK type, e.g., BlobClient, QueueClient, etc.
 * 1) parseMetadata(context) -> fill internal fields
 * 2) hydrate() -> internally calls hydrator to reflectively create the final SDK client
 */
public abstract class SdkType {
    /**
     * Gathers all necessary data from the worker's invocation context
     * (e.g. containerName, blobName, connection env var, etc.).
     */
    public abstract void parseMetadata(ExecutionContextDataSource execCtx) throws Exception;

    /**
     * Returns the hydrator instance for this SdkType.
     * Typically, a static final field or something similar.
     */
    protected abstract SdkTypeHydrator<? extends SdkType> getHydrator();

    /**
     * Triggers the reflection-based creation of the actual SDK client.
     * We do not supply defaults for missing data. If parseMetadata didn't fill the fields, this should fail.
     */
    public Object hydrate() throws Exception {
        @SuppressWarnings("unchecked")
        SdkTypeHydrator<SdkType> hydrator = (SdkTypeHydrator<SdkType>) getHydrator();
        return hydrator.createInstance(this);
    }
}