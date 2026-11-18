package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.rpc.messages.TypedData;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The raw model binding envelopes of one parameter plus the Functions host endpoint. */
public final class WorkerSdkInvocationContext implements SdkInvocationContext {
    private final List<ModelBindingData> modelBindingData;
    private final URI hostEndpoint;

    /**
     * @param input the host input bound to the parameter, or null if there is none
     * @param hostEndpoint the worker's Functions host endpoint, or null if unknown
     */
    public WorkerSdkInvocationContext(TypedData input, URI hostEndpoint) {
        List<ModelBindingData> envelopes = new ArrayList<>();
        if (input != null && input.hasModelBindingData()) {
            envelopes.add(convert(input.getModelBindingData()));
        } else if (input != null && input.hasCollectionModelBindingData()) {
            for (com.microsoft.azure.functions.rpc.messages.ModelBindingData item
                    : input.getCollectionModelBindingData().getModelBindingDataList()) {
                envelopes.add(convert(item));
            }
        }
        this.modelBindingData = Collections.unmodifiableList(envelopes);
        this.hostEndpoint = hostEndpoint;
    }

    private static ModelBindingData convert(com.microsoft.azure.functions.rpc.messages.ModelBindingData data) {
        return new ModelBindingData(data.getVersion(), data.getSource(), data.getContentType(),
                data.getContent().toByteArray());
    }

    @Override
    public List<ModelBindingData> getModelBindingData() {
        return modelBindingData;
    }

    @Override
    public URI getHostEndpoint() {
        return hostEndpoint;
    }
}
