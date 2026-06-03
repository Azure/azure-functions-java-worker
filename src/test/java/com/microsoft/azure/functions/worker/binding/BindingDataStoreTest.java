package com.microsoft.azure.functions.worker.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.UUID;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.Builder;
import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;

import org.junit.jupiter.api.Test;

public class BindingDataStoreTest {

    @Test
    public void getHttpResponseRawBodyReturnsNullWhenNoTargetsPromoted() {
        BindingDataStore store = new BindingDataStore();
        assertNull(store.getHttpResponseRawBody());
    }

    @Test
    public void getHttpResponseRawBodyReturnsNullWhenPromotedUuidHasNoTargets() {
        BindingDataStore store = new BindingDataStore();
        // Promote a UUID that was never populated.
        store.promoteDataTargets(UUID.randomUUID());
        assertNull(store.getHttpResponseRawBody());
    }

    @Test
    public void getHttpResponseRawBodyReturnsNullWhenHttpTargetHasNoBody() {
        BindingDataStore store = registerHttpReturnTarget(builder -> { /* no body */ });
        assertNull(store.getHttpResponseRawBody());
    }

    @Test
    public void getHttpResponseRawBodyReturnsInputStreamBody() {
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        BindingDataStore store = registerHttpReturnTarget(builder -> builder.body(stream));
        Object raw = store.getHttpResponseRawBody();
        assertSame(stream, raw);
        assertTrue(raw instanceof InputStream);
    }

    @Test
    public void getHttpResponseRawBodyReturnsIOConsumerBody() {
        IOConsumer<java.io.OutputStream> writer = out -> out.write(0);
        BindingDataStore store = registerHttpReturnTarget(builder -> builder.body(writer));
        Object raw = store.getHttpResponseRawBody();
        assertSame(writer, raw);
        assertTrue(raw instanceof IOConsumer);
    }

    @Test
    public void getHttpResponseRawBodyReturnsStringBody() {
        BindingDataStore store = registerHttpReturnTarget(builder -> builder.body("hello"));
        assertEquals("hello", store.getHttpResponseRawBody());
    }

    /**
     * Helper that sets up a store with a single promoted HTTP output target on
     * the {@code $return} binding, then invokes {@code configure} on the
     * underlying {@link HttpResponseMessage.Builder}.
     */
    @FunctionalInterface
    private interface BuilderConfigurator {
        void apply(Builder builder);
    }

    private static BindingDataStore registerHttpReturnTarget(BuilderConfigurator configure) {
        BindingDataStore store = new BindingDataStore();
        // getOrAddDataTarget internally consults `definitions` via isDefinitionOutput
        // before short-circuiting on ignoreDefinition; install an empty map so that
        // lookup returns Optional.empty() instead of throwing NPE.
        store.setBindingDefinitions(new HashMap<>());
        UUID outputId = UUID.randomUUID();
        // ignoreDefinition=true bypasses the binding-definition check, which is
        // adequate for unit-testing the data-store accessor in isolation.
        BindingData data = store.getOrAddDataTarget(
            outputId, BindingDataStore.RETURN_NAME, HttpResponseMessage.class, true).orElseThrow(
                () -> new AssertionError("Expected getOrAddDataTarget to create an HTTP target"));
        // RpcHttpDataTarget sets its own DataTarget value to `this` in its
        // constructor, so the BindingData value is the Builder itself.
        Object value = data.getValue();
        assertTrue(value instanceof Builder,
            "Expected RpcHttpDataTarget value to be an HttpResponseMessage.Builder, got " + value);
        configure.apply((Builder) value);
        store.promoteDataTargets(outputId);
        return store;
    }
}
