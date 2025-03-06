package com.microsoft.azure.functions.worker.sdktype;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;

/**
 * A registry that knows about recognized SDK client FQCNs and can create SdkType objects.
 */
public class SdkTypeRegistry {
    // Maps FQCN -> Class<? extends SdkType>
    private final Map<String, SdkTypeFactory> knownTypes = new HashMap<>();

    public SdkTypeRegistry() {
        registerBlobClient();
    }

    /** Check if we recognize a param type */
    public boolean isRecognizedType(String fqcn) {
        return knownTypes.containsKey(fqcn);
    }

    /** Create an SdkType object for the given recognized type */
    public SdkType<?> createSdkType(String fqcn, Parameter param) throws Exception {
        SdkTypeFactory factory = knownTypes.get(fqcn);
        if (factory == null) {
            throw new IllegalArgumentException("Unrecognized SdkType: " + fqcn);
        }
        return factory.create(param);
    }

    public interface SdkTypeFactory {
        SdkType create(Parameter param) throws Exception;
    }

    private void registerBlobClient() {
        SdkTypeHydrator<BlobClientSdkType> hydrator = new BlobClientHydrator();
        SdkTypeVerifier<BlobClientSdkType> verifier = new BlobClientVerifier();
        knownTypes.put("com.azure.storage.blob.BlobClient",
                (Parameter param) -> new BlobClientSdkType(hydrator, verifier, param));
    }
}
