package com.microsoft.azure.webjobs.script.binding;

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
        METADATA_NAME(1),
        TYPE_ASSIGNMENT(2),
        TYPE_STRICT_CONVERSION(3),
        TYPE_RELAXED_CONVERSION(4);
        public static int count() { return 5; }

        MatchingLevel(int index) {
            this.index = index;
        }
        public int getIndex() { return index; }
        private int index;
    }

    private final Object value;
    private MatchingLevel level;
}
