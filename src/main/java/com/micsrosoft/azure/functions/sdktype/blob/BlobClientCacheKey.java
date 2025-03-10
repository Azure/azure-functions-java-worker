package com.micsrosoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import java.util.Objects;

/**
 * POJO that implements CacheKey for storing BlobClient config.
 */
public class BlobClientCacheKey implements CacheKey {
    private final String containerName;
    private final String blobName;
    private final String envVarForConnection;

    public BlobClientCacheKey(String containerName, String blobName, String envVarForConnection) {
        this.containerName = containerName;
        this.blobName = blobName;
        this.envVarForConnection = envVarForConnection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlobClientCacheKey)) return false;
        BlobClientCacheKey that = (BlobClientCacheKey) o;
        return Objects.equals(containerName, that.containerName)
                && Objects.equals(blobName, that.blobName)
                && Objects.equals(envVarForConnection, that.envVarForConnection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerName, blobName, envVarForConnection);
    }

    @Override
    public String toString() {
        return "BlobClientCacheKey{" +
                "containerName='" + containerName + '\'' +
                ", blobName='" + blobName + '\'' +
                ", envVarForConnection='" + envVarForConnection + '\'' +
                '}';
    }
}
