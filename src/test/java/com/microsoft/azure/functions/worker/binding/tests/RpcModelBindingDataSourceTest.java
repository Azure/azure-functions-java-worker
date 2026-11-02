package com.microsoft.azure.functions.worker.binding.tests;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcModelBindingDataSource;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RpcModelBindingDataSourceTest {
    private static ModelBindingData data(ByteString content) {
        return ModelBindingData.newBuilder().setVersion("1.0").setContentType("application/json")
                .setContent(content).build();
    }

    @Test
    public void contentIsOnlyParsedWhenANameIsLookedUp() {
        ByteString binary = ByteString.copyFrom(new byte[] {(byte) 0xff, 0x00, 0x53, 0x75, (byte) 0xa0});

        RpcModelBindingDataSource source = new RpcModelBindingDataSource("message", data(binary));

        assertThrows(RuntimeException.class, () -> source.computeByName("ContainerName", String.class));
    }

    @Test
    public void jsonContentIsStillLookedUpByKey() {
        RpcModelBindingDataSource source = new RpcModelBindingDataSource("blob",
                data(ByteString.copyFromUtf8("{\"ContainerName\":\"photos\",\"BlobName\":\"a.txt\"}")));

        Optional<BindingData> container = source.computeByName("ContainerName", String.class);
        Optional<BindingData> missing = source.computeByName("Other", String.class);

        assertEquals("photos", container.get().getValue());
        assertTrue(!missing.isPresent());
    }

    @Test
    public void emptyContentNamesTheBindingWhenLookedUp() {
        RpcModelBindingDataSource source = new RpcModelBindingDataSource("testBlob", data(ByteString.EMPTY));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> source.computeByName("ContainerName", String.class));

        assertTrue(error.getMessage().contains("testBlob"), error.getMessage());
    }
}
