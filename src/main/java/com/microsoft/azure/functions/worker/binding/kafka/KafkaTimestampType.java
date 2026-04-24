// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.azure.functions.worker.binding.kafka;

/**
 * Defines the type of a Kafka record timestamp.
 */
public enum KafkaTimestampType {
    /**
     * Timestamp type is not available.
     */
    NotAvailable(0),

    /**
     * Timestamp was set by the producer (record creation time).
     */
    CreateTime(1),

    /**
     * Timestamp was set by the broker (log append time).
     */
    LogAppendTime(2);

    private final int value;

    KafkaTimestampType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    /**
     * Returns the enum constant for the given int value, or {@code NotAvailable} if not recognized.
     */
    public static KafkaTimestampType fromValue(int value) {
        for (KafkaTimestampType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NotAvailable;
    }
}
