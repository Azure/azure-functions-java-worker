package com.microsoft.azure.functions.worker.binding;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.internal.spi.sdk.ModelBindingData;
import com.microsoft.azure.functions.rpc.messages.CollectionModelBindingData;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WorkerSdkInvocationContextTest {
    @Test
    void preservesSingleAndOrderedBatchEnvelopesWithoutJsonParsing() throws Exception {
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0, 83, 115, -1};
        com.microsoft.azure.functions.rpc.messages.ModelBindingData first = envelope(bytes);
        com.microsoft.azure.functions.rpc.messages.ModelBindingData second = envelope(new byte[] {2});
        List<ParameterBinding> inputs = Arrays.asList(
                binding("single", TypedData.newBuilder().setModelBindingData(first).build()),
                binding("batch", TypedData.newBuilder().setCollectionModelBindingData(
                        CollectionModelBindingData.newBuilder().addModelBindingData(first)
                                .addModelBindingData(second)).build()));
        BindingDataStore store = new BindingDataStore();
        assertDoesNotThrow(() -> store.addParameterSources(inputs));
        assertFalse(store.getDataByNameFromInputSource("ContainerName", String.class, "single").isPresent());

        URI endpoint = URI.create("https://localhost:1234");
        WorkerSdkInvocationContext context = new WorkerSdkInvocationContext("invocation", endpoint, inputs);
        assertEquals("invocation", context.getInvocationId());
        assertEquals(endpoint, context.getHostEndpoint());
        assertTrue(context.isActive());
        ModelBindingData actual = context.getModelBindingData("single").get(0);
        assertEquals("1.0", actual.getVersion());
        assertEquals("AzureServiceBusReceivedMessage", actual.getSource());
        assertEquals("application/octet-stream", actual.getContentType());
        assertArrayEquals(bytes, actual.getContent());
        assertArrayEquals(bytes, context.getModelBindingData("batch").get(0).getContent());
        assertArrayEquals(new byte[] {2}, context.getModelBindingData("batch").get(1).getContent());
        assertThrows(UnsupportedOperationException.class, () -> context.getModelBindingData("single").clear());
        byte[] copy = actual.getContent();
        copy[0] = 42;
        assertArrayEquals(bytes, actual.getContent());
        assertTrue(context.getModelBindingData("absent").isEmpty());
        context.close();
        context.close();
        assertFalse(context.isActive());
    }

    @Test
    void closesResourcesOnceInReverseOrderAfterInvalidation() throws Exception {
        WorkerSdkInvocationContext context = new WorkerSdkInvocationContext("id", null, Collections.emptyList());
        List<Integer> closed = new ArrayList<>();
        context.registerCloseable(() -> {
            assertFalse(context.isActive());
            closed.add(1);
        });
        context.registerCloseable(() -> closed.add(2));
        assertThrows(NullPointerException.class, () -> context.registerCloseable(null));
        context.close();
        context.close();
        assertEquals(Arrays.asList(2, 1), closed);
        assertThrows(IllegalStateException.class, () -> context.registerCloseable(() -> { }));
    }

    @Test
    void cleanupContinuesAfterFailureAndPreservesAllFailures() {
        WorkerSdkInvocationContext context = new WorkerSdkInvocationContext("id", null, Collections.emptyList());
        IllegalStateException earlier = new IllegalStateException("earlier");
        IllegalArgumentException later = new IllegalArgumentException("later");
        context.registerCloseable(() -> { throw earlier; });
        context.registerCloseable(() -> { throw later; });
        assertSame(later, assertThrows(IllegalArgumentException.class, context::close));
        assertArrayEquals(new Throwable[] {earlier}, later.getSuppressed());
        assertFalse(context.isActive());
        assertDoesNotThrow(context::close);
    }

    @Test
    void bodyBindingsAndEmptyBatchesRemainSupportedWithoutAnEndpoint() {
        List<ParameterBinding> inputs = Arrays.asList(
                binding("body", TypedData.newBuilder().setString("plain message").build()),
                binding("batch", TypedData.newBuilder().setCollectionModelBindingData(
                        CollectionModelBindingData.getDefaultInstance()).build()));
        BindingDataStore store = new BindingDataStore();
        store.addParameterSources(inputs);
        assertEquals("plain message", store.getDataByName("body", String.class).get().getValue());
        WorkerSdkInvocationContext context = new WorkerSdkInvocationContext("id", null, inputs);
        assertNull(context.getHostEndpoint());
        assertTrue(context.getModelBindingData("body").isEmpty());
        assertTrue(context.getModelBindingData("batch").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", ""})
    void blobRequiredFieldsStillResolveLazily(String contentType) {
        TypedData blob = TypedData.newBuilder().setModelBindingData(
                com.microsoft.azure.functions.rpc.messages.ModelBindingData.newBuilder()
                        .setVersion("1.0").setSource("AzureStorageBlobs").setContentType(contentType)
                        .setContent(ByteString.copyFromUtf8("{\"ContainerName\":\"photos\",\"BlobName\":\"a.jpg\"}")))
                .build();
        BindingDataStore store = new BindingDataStore();
        store.addParameterSources(Collections.singletonList(binding("blob", blob)));
        assertEquals("photos", store.getDataByNameFromInputSource(
                "ContainerName", String.class, "blob").get().getValue());
        assertEquals("a.jpg", store.getDataByNameFromInputSource(
                "BlobName", String.class, "blob").get().getValue());
        assertFalse(store.getDataByNameFromInputSource("missing", String.class, "blob").isPresent());
    }

    @Test
    void malformedJsonFailsWhenRequiredFieldsAreRequestedNotOnArrival() {
        BindingDataStore store = new BindingDataStore();
        assertDoesNotThrow(() -> store.addParameterSources(Collections.singletonList(binding("blob",
                TypedData.newBuilder().setModelBindingData(envelope(new byte[] {-1}).toBuilder()
                        .setContentType("application/json")).build()))));
        assertThrows(RuntimeException.class,
                () -> store.getDataByNameFromInputSource("ContainerName", String.class, "blob"));
    }

    private static com.microsoft.azure.functions.rpc.messages.ModelBindingData envelope(byte[] bytes) {
        return com.microsoft.azure.functions.rpc.messages.ModelBindingData.newBuilder()
                .setVersion("1.0").setSource("AzureServiceBusReceivedMessage").setContentType("application/octet-stream")
                .setContent(ByteString.copyFrom(bytes)).build();
    }

    private static ParameterBinding binding(String name, TypedData data) {
        return ParameterBinding.newBuilder().setName(name).setData(data).build();
    }
}
