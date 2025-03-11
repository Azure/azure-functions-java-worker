package com.micsrosoft.azure.functions.sdktype.blob;

import com.micsrosoft.azure.functions.sdktype.SdkType;
import com.micsrosoft.azure.functions.sdktype.SdkTypeHydrator;

import java.lang.reflect.Parameter;

/**
 * SdkType for building a BlobClient. The parseMetadata method obtains
 * containerName, blobName, and envVarForConnection from the invocation context.
 */
public class BlobClientSdkType implements SdkType<BlobClientMetaData> {
    private final BlobClientHydrator hydrator;
    private final BlobClientMetaData metaData;
    private final Parameter param;

    public BlobClientSdkType(Parameter param) {
        this.hydrator = new BlobClientHydrator();
        this.metaData = new BlobClientMetaData();
        this.param = param;
    }

    @Override
    public BlobClientMetaData getMetaData() {
        return metaData;
    }

    @Override
    public SdkTypeHydrator<BlobClientMetaData> getHydrator() {
        return hydrator;
    }

    @Override
    public Parameter getParam() {
        return param;
    }
}