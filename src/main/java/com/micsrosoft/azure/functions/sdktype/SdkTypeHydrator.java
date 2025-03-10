package com.micsrosoft.azure.functions.sdktype;

/**
 * Reflection or direct logic to create an instance of T.
 *
 * @param <T> The specific SdkType
 */
public interface SdkTypeHydrator<T extends SdkType<T>> {
    Object createInstance(T sdkType) throws Exception;
}