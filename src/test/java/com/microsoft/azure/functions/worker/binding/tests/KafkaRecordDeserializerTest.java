package com.microsoft.azure.functions.worker.binding.tests;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.worker.binding.kafka.*;
import com.microsoft.azure.functions.worker.binding.kafka.proto.KafkaRecordProtos.*;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class KafkaRecordDeserializerTest {

    @Test
    public void deserialize_fullRecord_allFieldsPreserved() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("my-topic")
                .setPartition(3)
                .setOffset(12345)
                .setKey(ByteString.copyFromUtf8("my-key"))
                .setValue(ByteString.copyFromUtf8("{\"name\":\"test\"}"))
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(1700000000000L)
                        .setType(1) // CreateTime
                        .build())
                .setLeaderEpoch(7)
                .addHeaders(KafkaHeaderProto.newBuilder()
                        .setKey("trace-id")
                        .setValue(ByteString.copyFromUtf8("trace-abc"))
                        .build())
                .build();

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertEquals("my-topic", record.getTopic());
        assertEquals(3, record.getPartition());
        assertEquals(12345, record.getOffset());
        assertEquals("my-key", record.getKeyAsString());
        assertEquals("{\"name\":\"test\"}", record.getValueAsString());
        assertEquals(1700000000000L, record.getTimestamp().getUnixTimestampMs());
        assertEquals(KafkaTimestampType.CreateTime, record.getTimestamp().getType());
        assertEquals(7, record.getLeaderEpoch());
        assertEquals(1, record.getHeaders().size());
        assertEquals("trace-id", record.getHeaders().get(0).getKey());
        assertEquals("trace-abc", record.getHeaders().get(0).getValueAsString());
    }

    @Test
    public void deserialize_nullKeyAndValue() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("test-topic")
                .setPartition(0)
                .setOffset(100)
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(1700000000000L)
                        .setType(0)
                        .build())
                .build();
        // Key and Value deliberately not set

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertNull(record.getKey());
        assertNull(record.getValue());
        assertNull(record.getKeyAsString());
        assertNull(record.getValueAsString());
        assertEquals("test-topic", record.getTopic());
    }

    @Test
    public void deserialize_noLeaderEpoch_returnsNull() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("test-topic")
                .setPartition(0)
                .setOffset(0)
                .setValue(ByteString.copyFromUtf8("test"))
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(0)
                        .setType(0)
                        .build())
                .build();

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertNull(record.getLeaderEpoch());
    }

    @Test
    public void deserialize_unknownTimestampType_fallsBackToNotAvailable() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("test-topic")
                .setPartition(0)
                .setOffset(0)
                .setValue(ByteString.copyFromUtf8("test"))
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(1700000000000L)
                        .setType(99) // Unknown future value
                        .build())
                .build();

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertEquals(KafkaTimestampType.NotAvailable, record.getTimestamp().getType());
        assertEquals(1700000000000L, record.getTimestamp().getUnixTimestampMs());
    }

    @Test
    public void deserialize_multipleHeaders() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("test-topic")
                .setPartition(0)
                .setOffset(0)
                .setValue(ByteString.copyFromUtf8("test"))
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(0)
                        .setType(0)
                        .build())
                .addHeaders(KafkaHeaderProto.newBuilder()
                        .setKey("correlation-id")
                        .setValue(ByteString.copyFromUtf8("abc-123"))
                        .build())
                .addHeaders(KafkaHeaderProto.newBuilder()
                        .setKey("null-value-header")
                        // Value intentionally not set
                        .build())
                .build();

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertEquals(2, record.getHeaders().size());
        assertEquals("correlation-id", record.getHeaders().get(0).getKey());
        assertEquals("abc-123", record.getHeaders().get(0).getValueAsString());
        assertEquals("null-value-header", record.getHeaders().get(1).getKey());
        assertNull(record.getHeaders().get(1).getValue());
    }

    @Test
    public void deserialize_timestampDateTimeOffset() throws Exception {
        KafkaRecordProto proto = KafkaRecordProto.newBuilder()
                .setTopic("test-topic")
                .setPartition(0)
                .setOffset(0)
                .setValue(ByteString.copyFromUtf8("test"))
                .setTimestamp(KafkaTimestampProto.newBuilder()
                        .setUnixTimestampMs(1700000000000L)
                        .setType(2) // LogAppendTime
                        .build())
                .build();

        KafkaRecord record = KafkaRecordProtoDeserializer.deserialize(proto.toByteArray());

        assertEquals(KafkaTimestampType.LogAppendTime, record.getTimestamp().getType());
        assertNotNull(record.getTimestamp().getDateTimeOffset());
        assertEquals(1700000000000L, record.getTimestamp().getDateTimeOffset().toInstant().toEpochMilli());
    }

    @Test
    public void kafkaTimestampType_fromValue_allValues() {
        assertEquals(KafkaTimestampType.NotAvailable, KafkaTimestampType.fromValue(0));
        assertEquals(KafkaTimestampType.CreateTime, KafkaTimestampType.fromValue(1));
        assertEquals(KafkaTimestampType.LogAppendTime, KafkaTimestampType.fromValue(2));
        assertEquals(KafkaTimestampType.NotAvailable, KafkaTimestampType.fromValue(99));
        assertEquals(KafkaTimestampType.NotAvailable, KafkaTimestampType.fromValue(-1));
    }

    @Test
    public void kafkaHeader_getValueAsString_nullValue() {
        KafkaHeader header = new KafkaHeader("key", null);
        assertNull(header.getValueAsString());
    }

    @Test
    public void kafkaHeader_getValueAsString_withValue() {
        KafkaHeader header = new KafkaHeader("key", "hello".getBytes(StandardCharsets.UTF_8));
        assertEquals("hello", header.getValueAsString());
    }
}
