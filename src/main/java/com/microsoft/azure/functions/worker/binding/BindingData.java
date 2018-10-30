package com.microsoft.azure.functions.worker.binding;

import org.apache.commons.lang3.*;

/**
 * Provides the information such as the matching level of the actual value retrieved/converted from BindingDataStore.
 */
public final class BindingData {
    public BindingData(Object value) {
        this.value = value;     
    }

    Object getNullSafeValue() { return this.value; }
    public Object getValue() { return this.getNullSafeValue() == ObjectUtils.NULL ? null : this.getNullSafeValue(); }      

    private final Object value;
    
}
