package com.micsrosoft.azure.functions.sdktype.blob;

import com.micsrosoft.azure.functions.sdktype.SdkTypeHydrator;

import java.lang.reflect.Method;

/**
 * Reflection logic for building a BlobClient from a BlobClientSdkType.
 * No defaults for missing data. parseMetadata ensures required fields are set.
 */
public class BlobClientHydrator implements SdkTypeHydrator<BlobClientMetaData> {

    @Override
    public Object createInstance(BlobClientMetaData metaData) throws Exception {
        String containerName = metaData.getContainerName();
        String blobName = metaData.getBlobName();
        String envVar = metaData.getConnectionEnvVar();

        String connectionString = System.getenv(envVar);
        if (connectionString == null || connectionString.isEmpty()) {
            throw new IllegalArgumentException("No environment variable set for: " + envVar);
        }

        // Reflection over com.azure.storage.blob.BlobClientBuilder
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // TODO: add try-catch and error
        Class<?> builderClass = classLoader.loadClass("com.azure.storage.blob.BlobClientBuilder");
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