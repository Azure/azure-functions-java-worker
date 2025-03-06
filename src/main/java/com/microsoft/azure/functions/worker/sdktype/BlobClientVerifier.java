package com.microsoft.azure.functions.worker.sdktype;

public class BlobClientVerifier implements SdkTypeVerifier<BlobClientSdkType> {
    @Override
    public void verify(BlobClientSdkType sdkType) throws Exception {
        // do advanced checks, e.g. confirm library presence or triggers
        if (sdkType.getContainerName().isEmpty()) {
            throw new IllegalArgumentException("ContainerName cannot be empty");
        }
        // ...
    }
}
