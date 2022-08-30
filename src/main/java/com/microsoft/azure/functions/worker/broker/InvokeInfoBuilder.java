package com.microsoft.azure.functions.worker.broker;

import java.util.UUID;

public final class InvokeInfoBuilder extends JavaMethodInvokeInfo.Builder {
    public InvokeInfoBuilder(MethodBindInfo method) { super.setMethod(method.getEntry()); }
    private final UUID outputsId = UUID.randomUUID();

    public UUID getOutputsId() {
        return outputsId;
    }
}
