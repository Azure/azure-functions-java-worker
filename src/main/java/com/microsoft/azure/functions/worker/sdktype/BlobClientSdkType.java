package com.microsoft.azure.functions.worker.sdktype;

import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.RpcModelBindingDataSource;

/**
 * SdkType for building a BlobClient. The parseMetadata method obtains
 * containerName, blobName, and envVarForConnection from the invocation context.
 */
public class BlobClientSdkType extends SdkType {
    private static final BlobClientHydrator HYDRATOR = new BlobClientHydrator();

    private String containerName;
    private String blobName;
    private String envVarForConnectionString;

    @Override
    public void parseMetadata(ExecutionContextDataSource execCtx) throws Exception {
        BindingDataStore dataStore = execCtx.getDataStore();

         Object mbd = dataStore.getDataByName("content", RpcModelBindingDataSource.class);

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
    protected SdkTypeHydrator<BlobClientSdkType> getHydrator() {
        return HYDRATOR;
    }

    // Getters for the hydrator
    public String getContainerName() { return containerName; }
    public String getBlobName() { return blobName; }
    public String getEnvVarForConnectionString() { return envVarForConnectionString; }
}