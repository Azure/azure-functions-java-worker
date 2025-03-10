package com.micsrosoft.azure.functions.sdktype;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

public class SdkParameterAnalyzer {
    private final SdkTypeRegistry registry = new SdkTypeRegistry();

    public SdkParameterAnalysisResult analyze(Method method) {
        SdkParameterAnalysisResult result = new SdkParameterAnalysisResult();
        for (Parameter param : method.getParameters()) {
            String fqcn = param.getType().getName();
            if (registry.isRecognizedType(fqcn)) {
                try {
                    SdkType<?> sdkType = registry.createSdkType(fqcn, param);
                    result.addSdkType(sdkType);
                } catch (Exception e) {
                    // Wrap in a runtime exception
                    throw new RuntimeException(
                            "Failed to create SdkType for " + fqcn + ": " + e.getMessage(), e
                    );
                }
            }
        }

        // TODO: Throw exception if more than one sdktype is detected
        return result;
    }
}