package com.micsrosoft.azure.functions.sdktype;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;

import java.lang.reflect.Parameter;

/**
 * A recognized SDK type that:
 *   - Has references to a hydrator and verifier
 *   - Knows how to parse invocation metadata
 *   - Can produce a CacheKey (if needed)
 *   - Has default methods for verify() and buildInstance() that rely on getVerifier(), getHydrator().
 *
 * @param <T> The concrete type implementing SdkType (for safe casting in hydrators/verifiers).
 */
public interface SdkType<T extends SdkType<T>> {

    /**
     * Gather necessary fields from the invocation context
     * (e.g., containerName, blobName, etc.).
     */
    void parseMetadata(MiddlewareContext context) throws Exception;

    /**
     * Return a SdkTypeVerifier (if any) for advanced checks.
     */
    SdkTypeVerifier<T> getVerifier();

    /**
     * Return a SdkTypeHydrator (if any) for reflection-based creation.
     */
    SdkTypeHydrator<T> getHydrator();

    /**
     * Return a Parameter object for the argument that uses the SDK type.
     */
    Parameter getParam();

    /**
     * Optionally build a CacheKey for caching.
     * Return null if no caching is desired.
     */
    CacheKey buildCacheKey();

    /**
     * Default method to run advanced checks.
     * Calls getVerifier().verify(this) if present.
     */
    default void verify() throws Exception {
        SdkTypeVerifier<T> verifier = getVerifier();
        if (verifier != null) {
            @SuppressWarnings("unchecked")
            T self = (T) this;
            verifier.verify(self);
        }
    }

    /**
     * Default method to build the final object by calling getHydrator().
     */
    default Object buildInstance() throws Exception {
        SdkTypeHydrator<T> hydrator = getHydrator();
        if (hydrator == null) {
            throw new IllegalStateException("No hydrator provided in this SdkType");
        }
        @SuppressWarnings("unchecked")
        T self = (T) this;
        return hydrator.createInstance(self);
    }
}