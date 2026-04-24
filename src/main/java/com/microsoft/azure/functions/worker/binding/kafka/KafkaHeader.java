// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.azure.functions.worker.binding.kafka;

import java.nio.charset.StandardCharsets;

/**
 * Represents a single Kafka record header (key-value pair where value is raw bytes).
 */
public class KafkaHeader {
    private final String key;
    private final byte[] value;

    public KafkaHeader(String key, byte[] value) {
        this.key = key;
        this.value = value;
    }

    /**
     * Returns the header key.
     */
    public String getKey() {
        return key;
    }

    /**
     * Returns the header value as raw bytes, or null if not present.
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * Returns the header value as a UTF-8 string, or null if the value is null.
     */
    public String getValueAsString() {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }
}
