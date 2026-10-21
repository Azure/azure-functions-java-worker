package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** One SDK scope shared by all parameters and middleware in an invocation. */
public final class WorkerSdkInvocationContext implements SdkInvocationContext, AutoCloseable {
    private final String invocationId;
    private final URI hostEndpoint;
    private final Map<String, List<ModelBindingData>> bindings;
    private final List<AutoCloseable> resources = new ArrayList<>();
    private volatile boolean active = true;

    public WorkerSdkInvocationContext(String invocationId, URI hostEndpoint, List<ParameterBinding> parameters) {
        this.invocationId = invocationId;
        this.hostEndpoint = hostEndpoint;
        Map<String, List<ModelBindingData>> values = new HashMap<>();
        for (ParameterBinding parameter : parameters) {
            TypedData data = parameter.getData();
            List<ModelBindingData> envelopes = new ArrayList<>();
            if (data.hasModelBindingData()) {
                envelopes.add(convert(data.getModelBindingData()));
            } else if (data.hasCollectionModelBindingData()) {
                data.getCollectionModelBindingData().getModelBindingDataList()
                        .forEach(item -> envelopes.add(convert(item)));
            }
            if (data.hasModelBindingData() || data.hasCollectionModelBindingData()) {
                values.put(parameter.getName(), Collections.unmodifiableList(envelopes));
            }
        }
        this.bindings = Collections.unmodifiableMap(values);
    }

    private static ModelBindingData convert(com.microsoft.azure.functions.rpc.messages.ModelBindingData data) {
        return new ModelBindingData(data.getVersion(), data.getSource(), data.getContentType(),
                data.getContent().toByteArray());
    }

    @Override
    public String getInvocationId() {
        return invocationId;
    }

    @Override
    public List<ModelBindingData> getModelBindingData(String bindingName) {
        return bindings.getOrDefault(bindingName, Collections.emptyList());
    }

    @Override
    public URI getHostEndpoint() {
        return hostEndpoint;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public synchronized void registerCloseable(AutoCloseable resource) {
        Objects.requireNonNull(resource, "resource");
        if (!active) {
            throw new IllegalStateException("SDK invocation " + invocationId + " is no longer active");
        }
        resources.add(resource);
    }

    @Override
    public void close() throws Exception {
        List<AutoCloseable> toClose;
        synchronized (this) {
            if (!active) {
                return;
            }
            active = false;
            toClose = new ArrayList<>(resources);
            resources.clear();
        }
        Throwable failure = null;
        for (int i = toClose.size() - 1; i >= 0; i--) {
            try {
                toClose.get(i).close();
            } catch (Exception | Error ex) {
                if (failure == null) {
                    failure = ex;
                } else if (failure != ex) {
                    failure.addSuppressed(ex);
                }
            }
        }
        if (failure instanceof Exception) {
            throw (Exception) failure;
        }
        if (failure != null) {
            throw (Error) failure;
        }
    }
}
