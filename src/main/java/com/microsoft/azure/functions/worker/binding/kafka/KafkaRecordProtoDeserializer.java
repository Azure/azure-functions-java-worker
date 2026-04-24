// Copyright (c) .NET Foundation. All rights reserved.
// Licensed under the MIT License. See License.txt in the project root for license information.

package com.microsoft.azure.functions.worker.binding.kafka;

import com.google.protobuf.InvalidProtocolBufferException;
import com.microsoft.azure.functions.worker.binding.kafka.proto.KafkaRecordProtos.KafkaRecordProto;
import com.microsoft.azure.functions.worker.binding.kafka.proto.KafkaRecordProtos.KafkaHeaderProto;
import com.microsoft.azure.functions.worker.binding.kafka.proto.KafkaRecordProtos.KafkaTimestampProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deserializes Protobuf-encoded KafkaRecordProto bytes into a {@link KafkaRecord} POJO.
 */
public final class KafkaRecordProtoDeserializer {

    public static final String EXPECTED_SOURCE = "AzureKafkaRecord";
    public static final String EXPECTED_CONTENT_TYPE = "application/x-protobuf";

    private KafkaRecordProtoDeserializer() {
    }

    /**
     * Deserializes Protobuf bytes into a {@link KafkaRecord}.
     *
     * @param protoBytes the Protobuf-encoded bytes from ModelBindingData.content
     * @return a fully-populated KafkaRecord
     * @throws InvalidProtocolBufferException if the bytes are not valid Protobuf
     */
    public static KafkaRecord deserialize(byte[] protoBytes) throws InvalidProtocolBufferException {
        KafkaRecordProto proto = KafkaRecordProto.parseFrom(protoBytes);
        return fromProto(proto);
    }

    static KafkaRecord fromProto(KafkaRecordProto proto) {
        // Key: optional bytes — null if not present
        byte[] key = proto.hasKey() ? proto.getKey().toByteArray() : null;

        // Value: optional bytes — null if not present
        byte[] value = proto.hasValue() ? proto.getValue().toByteArray() : null;

        // Leader epoch: optional int32 — null if not present
        Integer leaderEpoch = proto.hasLeaderEpoch() ? proto.getLeaderEpoch() : null;

        // Timestamp
        KafkaTimestamp timestamp = null;
        if (proto.hasTimestamp()) {
            KafkaTimestampProto ts = proto.getTimestamp();
            timestamp = new KafkaTimestamp(
                    ts.getUnixTimestampMs(),
                    KafkaTimestampType.fromValue(ts.getType()));
        }

        // Headers
        List<KafkaHeader> headers;
        if (proto.getHeadersCount() > 0) {
            headers = new ArrayList<>(proto.getHeadersCount());
            for (KafkaHeaderProto h : proto.getHeadersList()) {
                byte[] headerValue = h.hasValue() ? h.getValue().toByteArray() : null;
                headers.add(new KafkaHeader(h.getKey(), headerValue));
            }
        } else {
            headers = Collections.emptyList();
        }

        return new KafkaRecord(
                proto.getTopic(),
                proto.getPartition(),
                proto.getOffset(),
                key,
                value,
                timestamp,
                headers,
                leaderEpoch);
    }
}
