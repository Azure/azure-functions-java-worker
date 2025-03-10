package com.micsrosoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.micsrosoft.azure.functions.sdktype.SdkType;
import com.micsrosoft.azure.functions.sdktype.SdkTypeHydrator;
import com.micsrosoft.azure.functions.sdktype.SdkTypeVerifier;

import java.lang.reflect.Parameter;

/**
 * SdkType for building a BlobClient. The parseMetadata method obtains
 * containerName, blobName, and envVarForConnection from the invocation context.
 */
public class BlobClientSdkType implements SdkType<BlobClientSdkType> {
    private final SdkTypeHydrator<BlobClientSdkType> hydrator;
    private final SdkTypeVerifier<BlobClientSdkType> verifier;
    private final Parameter param;
    private String containerName;
    private String blobName;
    private String envVarForConnectionString;

    public BlobClientSdkType(SdkTypeHydrator<BlobClientSdkType> hydrator,
                             SdkTypeVerifier<BlobClientSdkType> verifier,
                             Parameter param) {
        this.hydrator = hydrator;
        this.verifier = verifier;
        this.param = param;
    }

    @Override
    public void parseMetadata(MiddlewareContext context) throws Exception {
        ExecutionContextDataSource execCtx = (ExecutionContextDataSource) context;
        BindingDataStore dataStore = execCtx.getDataStore();

        // containerName
        this.containerName = (String) dataStore.getDataByName("ContainerName", String.class)
                .map(b -> b.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Missing containerName for BlobClientSdkType"));

        // blobName
        this.blobName = (String) dataStore.getDataByName("BlobName", String.class)
                .map(b -> b.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Missing blobName for BlobClientSdkType"));

        // envVarForConnectionString
        this.envVarForConnectionString = (String) dataStore.getDataByName("Connection", String.class)
                .map(b -> b.getValue())
                .orElseThrow(() -> new IllegalArgumentException("Missing envVarForConnectionString for BlobClientSdkType"));
    }

    @Override
    public CacheKey buildCacheKey() {
        // If we want caching, produce a key. Otherwise could return null.
        return new BlobClientCacheKey(containerName, blobName, envVarForConnectionString);
    }

    @Override
    public SdkTypeHydrator<BlobClientSdkType> getHydrator() {
        return hydrator;
    }

    @Override
    public SdkTypeVerifier<BlobClientSdkType> getVerifier() {
        return verifier;
    }

    @Override
    public Parameter getParam() {
        return param;
    }

    // Getters for the hydrator
    public String getContainerName() { return containerName; }
    public String getBlobName() { return blobName; }
    public String getEnvVarForConnectionString() { return envVarForConnectionString; }
}