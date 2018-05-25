package com.microsoft.azure.functions.worker.binding;

import org.apache.commons.lang3.*;

/**
 * Provides the information such as the matching level of the actual value retrieved/converted from BindingDataStore.
 */
public final class BindingData {
    BindingData(Object value, MatchingLevel level) {
        this.value = value;
        this.level = level;
    }

    Object getNullSafeValue() { return this.value; }
    public Object getValue() { return this.getNullSafeValue() == ObjectUtils.NULL ? null : this.getNullSafeValue(); }
    public MatchingLevel getLevel() { return level; }
    void setLevel(MatchingLevel level) { this.level = level; }

    /**
     * Represents how this value is retrieved. Higher index value means higher priority in the overload resolution algorithm.
     */
    public enum MatchingLevel {
        BINDING_NAME(0),
        TRIGGER_METADATA_NAME(1),
        METADATA_NAME(2),
        TYPE_ASSIGNMENT(3),
        TYPE_STRICT_CONVERSION(4),
        TYPE_RELAXED_CONVERSION(5);
        public static int count() { return 6; }

        MatchingLevel(int index) {
            this.index = index;
        }
        public int getIndex() { return index; }
        private int index;
    }

    private final Object value;
    private MatchingLevel level;
}
