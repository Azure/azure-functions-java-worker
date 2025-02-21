package com.microsoft.azure.functions.worker.sdktype;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps param-type FQCN -> SdkType class.
 * The SdkType class internally references its hydrator via getHydrator().
 */
public class SdkTypeRegistry {
    private static final Map<String, Class<? extends SdkType>> REGISTRY = new HashMap<>();

    static {
        // Register BlobClient
        REGISTRY.put("com.azure.storage.blob.BlobClient", BlobClientSdkType.class);
        // In future, add queue, table, etc.
    }

    public static boolean isRecognizedType(String fqcn) {
        return REGISTRY.containsKey(fqcn);
    }

    public static SdkType createSdkType(String fqcn) throws Exception {
        Class<? extends SdkType> sdkTypeClass = REGISTRY.get(fqcn);
        if (sdkTypeClass == null) {
            throw new IllegalArgumentException("Unrecognized sdkType: " + fqcn);
        }
        return sdkTypeClass.getDeclaredConstructor().newInstance();
    }
}