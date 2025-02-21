package com.microsoft.azure.functions.worker.sdktype;

/**
 * Separate interface for reflection logic:
 * createInstance(sdkType) builds the final Azure SDK client from the parsed fields.
 */
public interface SdkTypeHydrator<T extends SdkType> {
    Object createInstance(T sdkType) throws Exception;
}