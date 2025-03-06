package com.microsoft.azure.functions.worker.sdktype;

/**
 * Advanced usage checks for T (e.g., triggers, library presence).
 *
 * @param <T> The specific SdkType
 */
public interface SdkTypeVerifier<T extends SdkType<T>> {
    /**
     * If verification fails, throw an exception.
     */
    void verify(T sdkType) throws Exception;
}