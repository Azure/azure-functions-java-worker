package com.micsrosoft.azure.functions.sdktype;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;


/**
 * Holds discovered SdkTypes from analyzing a method.
 */
public class SdkParameterAnalysisResult {
    private final List<SdkType<?>> sdkTypes = new ArrayList<>();

    public void addSdkType(SdkType<?> sdkType) {
        this.sdkTypes.add(sdkType);
    }

    public List<SdkType<?>> getSdkTypes() {
        return Collections.unmodifiableList(sdkTypes);
    }

    public boolean hasAnySdkTypes() {
        return !sdkTypes.isEmpty();
    }
}