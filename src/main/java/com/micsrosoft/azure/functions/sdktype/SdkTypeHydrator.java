package com.micsrosoft.azure.functions.sdktype;

/**
 * A hydrator that builds the final client instance
 * from a given SdkTypeMetaData object.
 *
 * @param <M> the type of MetaData used
 */
public interface SdkTypeHydrator<M extends SdkTypeMetaData> {
    /**
     * Build the final object using data from the metaData.
     */
    Object createInstance(M metaData) throws Exception;
}