// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.azure.functions.worker.binding.kafka;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Represents a raw Apache Kafka record with full metadata.
 * Key and value are raw bytes — the user controls deserialization.
 */
public class KafkaRecord {
    private final String topic;
    private final int partition;
    private final long offset;
    private final byte[] key;
    private final byte[] value;
    private final KafkaTimestamp timestamp;
    private final List<KafkaHeader> headers;
    private final Integer leaderEpoch;

    public KafkaRecord(String topic, int partition, long offset, byte[] key, byte[] value,
                       KafkaTimestamp timestamp, List<KafkaHeader> headers, Integer leaderEpoch) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
        this.headers = headers;
        this.leaderEpoch = leaderEpoch;
    }

    /**
     * Returns the topic name this record was consumed from.
     */
    public String getTopic() {
        return topic;
    }

    /**
     * Returns the partition this record was consumed from.
     */
    public int getPartition() {
        return partition;
    }

    /**
     * Returns the offset of this record within the partition.
     */
    public long getOffset() {
        return offset;
    }

    /**
     * Returns the raw key bytes. Null if the record has no key.
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Returns the raw value bytes. Null if the record has no value.
     */
    public byte[] getValue() {
        return value;
    }

    /**
     * Returns the key as a UTF-8 string, or null if the key is null.
     */
    public String getKeyAsString() {
        return key == null ? null : new String(key, StandardCharsets.UTF_8);
    }

    /**
     * Returns the value as a UTF-8 string, or null if the value is null.
     */
    public String getValueAsString() {
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    /**
     * Returns the record timestamp.
     */
    public KafkaTimestamp getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the record headers.
     */
    public List<KafkaHeader> getHeaders() {
        return headers;
    }

    /**
     * Returns the leader epoch, if available. Null if not provided by the broker.
     */
    public Integer getLeaderEpoch() {
        return leaderEpoch;
    }
}
