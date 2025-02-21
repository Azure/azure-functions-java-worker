package com.microsoft.azure.functions.worker.sdktype;

import java.lang.reflect.Method;

/**
 * Reflection logic for building a BlobClient from a BlobClientSdkType.
 * No defaults for missing data. parseMetadata ensures required fields are set.
 */
public class BlobClientHydrator implements SdkTypeHydrator<BlobClientSdkType> {

    @Override
    public Object createInstance(BlobClientSdkType sdkType) throws Exception {
        String containerName = sdkType.getContainerName();
        String blobName = sdkType.getBlobName();
        String envVar = sdkType.getEnvVarForConnectionString();

        String connectionString = System.getenv(envVar);
        if (connectionString == null || connectionString.isEmpty()) {
            throw new IllegalArgumentException("No environment variable set for: " + envVar);
        }

        // Reflection over com.azure.storage.blob.BlobClientBuilder
        Class<?> builderClass = Class.forName("com.azure.storage.blob.BlobClientBuilder");
        Object builder = builderClass.getDeclaredConstructor().newInstance();

        Method conn = builderClass.getMethod("connectionString", String.class);
        conn.invoke(builder, connectionString);

        Method cont = builderClass.getMethod("containerName", String.class);
        cont.invoke(builder, containerName);

        Method bName = builderClass.getMethod("blobName", String.class);
        bName.invoke(builder, blobName);

        Method build = builderClass.getMethod("buildClient");
        return build.invoke(builder);
    }
}