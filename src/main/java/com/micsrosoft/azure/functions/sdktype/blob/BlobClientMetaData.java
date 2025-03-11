package com.micsrosoft.azure.functions.sdktype.blob;

import com.microsoft.azure.functions.cache.CacheKey;
import com.micsrosoft.azure.functions.sdktype.SdkTypeMetaData;

import java.util.*;

/**
 * Example metaData class for storing fields: containerName, blobName, etc.
 * We store them in a map or direct fields. We'll show a map approach.
 */
public class BlobClientMetaData implements SdkTypeMetaData {
    private final Map<String,Object> rawValues = new HashMap<>();

    // typed fields after parse
    private String containerName;
    private String blobName;
    private String connectionEnvVar;

    @Override
    public Set<String> getRequiredFields() {
        return Set.of("containerName","blobName","connectionEnvVar");
    }

    @Override
    public Object getFieldValue(String key) {
        return rawValues.get(key);
    }

    @Override
    public void setFieldValue(String key, Object value) {
        rawValues.put(key, value);
    }

    @Override
    public void parseAndVerify() {
        // transform the raw values into typed fields
        this.containerName = (String) rawValues.get("containerName");
        this.blobName = (String) rawValues.get("blobName");
        this.connectionEnvVar = (String) rawValues.get("connectionEnvVar");

        // do checks
        if (containerName == null || containerName.isEmpty()) {
            throw new IllegalArgumentException("containerName is required");
        }
        if (blobName == null || blobName.isEmpty()) {
            throw new IllegalArgumentException("blobName is required");
        }
        if (connectionEnvVar == null || connectionEnvVar.isEmpty()) {
            throw new IllegalArgumentException("connectionEnvVar is required");
        }
    }

    @Override
    public CacheKey buildCacheKey() {
        return new BlobClientCacheKey(containerName, blobName, connectionEnvVar);
    }

    public String getContainerName() { return containerName; }
    public String getBlobName() { return blobName; }
    public String getConnectionEnvVar() { return connectionEnvVar; }
}

