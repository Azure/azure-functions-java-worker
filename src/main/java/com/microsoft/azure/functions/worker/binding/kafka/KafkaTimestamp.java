// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.azure.functions.worker.binding.kafka;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Represents the timestamp of a Kafka record.
 */
public class KafkaTimestamp {
    private final long unixTimestampMs;
    private final KafkaTimestampType type;

    public KafkaTimestamp(long unixTimestampMs, KafkaTimestampType type) {
        this.unixTimestampMs = unixTimestampMs;
        this.type = type;
    }

    /**
     * Returns the timestamp as Unix milliseconds since epoch.
     */
    public long getUnixTimestampMs() {
        return unixTimestampMs;
    }

    /**
     * Returns the timestamp type.
     */
    public KafkaTimestampType getType() {
        return type;
    }

    /**
     * Returns the timestamp as an {@link OffsetDateTime} in UTC.
     */
    public OffsetDateTime getDateTimeOffset() {
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(unixTimestampMs), ZoneOffset.UTC);
    }
}
