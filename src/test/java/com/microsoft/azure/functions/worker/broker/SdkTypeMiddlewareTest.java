package com.microsoft.azure.functions.worker.broker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.rpc.messages.CollectionModelBindingData;
import com.microsoft.azure.functions.rpc.messages.ModelBindingData;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.blob.BlobClientMetaData;
import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkType;
import com.microsoft.azure.functions.sdktype.servicebus.ServiceBusSdkTypeFactory;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.chain.SdkTypeMiddleware;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class SdkTypeMiddlewareTest {
    private static final String BLOB_CLIENT = "com.azure.storage.blob.BlobClient";

    @Test
    void concurrentInvocationsOfOneFunctionDoNotShareBlobMetadata() throws Exception {
        CyclicBarrier bothFilled = new CyclicBarrier(2);
        SdkTypeRegistry registry = new SdkTypeRegistry() {
            @Override
            public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
                SdkType<?> blob = super.createSdkType(metaData);
                bothFilled.await(5, TimeUnit.SECONDS);
                return describeBlob((BlobClientSdkType) blob, new AtomicInteger());
            }
        };
        Method method = getClass().getDeclaredMethod("trigger", Object.class);
        SdkTypeMiddleware middleware = middleware(registry, method);
        WorkerObjectCache<CacheKey> cache = new WorkerObjectCache<>();
        ExecutionContextDataSource first = context(method, blob("photos", "a.txt"), cache);
        ExecutionContextDataSource second = context(method, blob("docs", "b.txt"), cache);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> invoke(middleware, first));
            Future<?> two = executor.submit(() -> invoke(middleware, second));
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals("photos/a.txt", first.getBindingData("blob", Object.class).get().getValue());
        assertEquals("docs/b.txt", second.getBindingData("blob", Object.class).get().getValue());
    }

    @Test
    void identicalBlobsAreStillBuiltOnceAndCached() throws Exception {
        AtomicInteger builds = new AtomicInteger();
        SdkTypeRegistry registry = new SdkTypeRegistry() {
            @Override
            public SdkType<?> createSdkType(SdkTypeMetaData metaData) throws Exception {
                return describeBlob((BlobClientSdkType) super.createSdkType(metaData), builds);
            }
        };
        Method method = getClass().getDeclaredMethod("trigger", Object.class);
        SdkTypeMiddleware middleware = middleware(registry, method);
        WorkerObjectCache<CacheKey> cache = new WorkerObjectCache<>();
        ExecutionContextDataSource first = context(method, blob("photos", "a.txt"), cache);
        ExecutionContextDataSource second = context(method, blob("photos", "a.txt"), cache);

        invoke(middleware, first);
        invoke(middleware, second);

        assertEquals(1, builds.get());
        assertSame(first.getBindingData("blob", Object.class).get().getValue(),
                second.getBindingData("blob", Object.class).get().getValue());
    }

    @Test
    void hydratorGetsTheRawEnvelopeAndEndpointWithoutJsonParsing() throws Exception {
        CapturingRegistry registry = new CapturingRegistry();
        Method method = getClass().getDeclaredMethod("serviceBusFunction", FakeMessage.class, FakeActions.class);
        URI endpoint = URI.create("http://localhost:7071");
        SdkTypeMiddleware middleware = middleware(registry, endpoint, method, ServiceBusSdkTypeFactory.MESSAGE);
        // .NET GUID prefix followed by AMQP bytes, which is not valid UTF-8 and not JSON
        byte[] content = {0x33, 0x22, 0x11, 0x00, 0x55, 0x44, 0x77, 0x66, (byte) 0x88, (byte) 0x99, (byte) 0xaa,
                (byte) 0xbb, (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff, 0x00, 0x53, 0x75, (byte) 0xa0,
                0x03, 0x61, 0x62, 0x63};

        invoke(middleware, context(method, Collections.singletonList(single("message", content)),
                new WorkerObjectCache<>()));

        SdkInvocationContext seen = registry.contexts.get(0);
        assertEquals(1, seen.getModelBindingData().size());
        assertEquals("1.0", seen.getModelBindingData().get(0).getVersion());
        assertEquals("AzureServiceBusReceivedMessage", seen.getModelBindingData().get(0).getSource());
        assertEquals("application/octet-stream", seen.getModelBindingData().get(0).getContentType());
        assertArrayEquals(content, seen.getModelBindingData().get(0).getContent());
        assertEquals(endpoint, seen.getHostEndpoint());
    }

    @Test
    void hydratorGetsEveryEnvelopeOfABatchInOrder() throws Exception {
        CapturingRegistry registry = new CapturingRegistry();
        Method method = getClass().getDeclaredMethod("serviceBusBatchFunction", List.class);
        SdkTypeMiddleware middleware = middleware(registry, null, method, ServiceBusSdkTypeFactory.MESSAGE);
        ParameterBinding batch = ParameterBinding.newBuilder().setName("messages")
                .setData(TypedData.newBuilder().setCollectionModelBindingData(CollectionModelBindingData.newBuilder()
                        .addModelBindingData(envelope(new byte[] {1}))
                        .addModelBindingData(envelope(new byte[] {2, 3}))))
                .build();

        invoke(middleware, context(method, Collections.singletonList(batch), new WorkerObjectCache<>()));

        SdkInvocationContext seen = registry.contexts.get(0);
        assertEquals(2, seen.getModelBindingData().size());
        assertArrayEquals(new byte[] {1}, seen.getModelBindingData().get(0).getContent());
        assertArrayEquals(new byte[] {2, 3}, seen.getModelBindingData().get(1).getContent());
        assertNull(seen.getHostEndpoint());
    }

    @Test
    void parameterWithoutNameIsBuiltWithoutPayloadAndResolvedByType() throws Exception {
        CapturingRegistry registry = new CapturingRegistry();
        Method method = getClass().getDeclaredMethod("serviceBusFunction", FakeMessage.class, FakeActions.class);
        SdkTypeMiddleware middleware = middleware(registry, null, method, ServiceBusSdkTypeFactory.MESSAGE,
                ServiceBusSdkTypeFactory.ACTIONS);
        ExecutionContextDataSource context = context(method,
                Collections.singletonList(single("message", new byte[] {1})), new WorkerObjectCache<>());

        invoke(middleware, context);
        CAPTURED_ARGUMENTS.set(null);
        ParameterResolver.resolveArguments(context).get().invoke(() -> null);

        Object[] arguments = CAPTURED_ARGUMENTS.get();
        assertTrue(arguments[0] instanceof FakeMessage);
        assertTrue(arguments[1] instanceof FakeActions);
        assertEquals(1, registry.contexts.get(0).getModelBindingData().size());
        assertTrue(registry.contexts.get(1).getModelBindingData().isEmpty());
    }

    @Test
    void plainPayloadForAnSdkParameterFailsWithAClearMessage() throws Exception {
        CapturingRegistry registry = new CapturingRegistry();
        Method method = getClass().getDeclaredMethod("serviceBusFunction", FakeMessage.class, FakeActions.class);
        SdkTypeMiddleware middleware = middleware(registry, null, method, ServiceBusSdkTypeFactory.MESSAGE);
        ParameterBinding plain = ParameterBinding.newBuilder().setName("message")
                .setData(TypedData.newBuilder().setString("hello")).build();
        ExecutionContextDataSource context = context(method, Collections.singletonList(plain),
                new WorkerObjectCache<>());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> middleware.invoke(context, next -> { }));

        assertTrue(error.getMessage().contains("'message'"), error.getMessage());
        assertTrue(error.getMessage().contains("'function'"), error.getMessage());
        assertTrue(error.getMessage().contains("STRING"), error.getMessage());
        assertTrue(registry.contexts.isEmpty());
    }

    @Test
    void unresolvedSdkParameterNamesTheFunctionAndTheSetting() throws Exception {
        // Without the middleware, which is the case when JAVA_ENABLE_SDK_TYPES is false
        Method method = getClass().getDeclaredMethod("serviceBusFunction", FakeMessage.class, FakeActions.class);
        byte[] json = "{\"ContainerName\":\"photos\"}".getBytes(StandardCharsets.UTF_8);
        ExecutionContextDataSource context = context(method,
                Collections.singletonList(single("message", json)), new WorkerObjectCache<>());

        WrongMethodTypeException error = assertThrows(WrongMethodTypeException.class,
                () -> ParameterResolver.resolveArguments(context));

        assertTrue(error.getMessage().contains("'message'"), error.getMessage());
        assertTrue(error.getMessage().contains("'function'"), error.getMessage());
        assertTrue(error.getMessage().contains("JAVA_ENABLE_SDK_TYPES"), error.getMessage());
    }

    /** Keeps the real Blob metadata and cache key, but replaces the hydrator so no Azure SDK is needed. */
    private static CachableSdkType<BlobClientMetaData> describeBlob(BlobClientSdkType blob, AtomicInteger builds) {
        return new CachableSdkType<BlobClientMetaData>() {
            @Override
            public BlobClientMetaData getMetaData() {
                return blob.getMetaData();
            }

            @Override
            public SdkTypeHydrator<BlobClientMetaData> getHydrator() {
                return metaData -> {
                    builds.incrementAndGet();
                    return metaData.getContainerName() + "/" + metaData.getBlobName();
                };
            }

            @Override
            public CacheKey buildCacheKey() {
                return blob.buildCacheKey();
            }
        };
    }

    private static SdkTypeMiddleware middleware(SdkTypeRegistry registry, URI endpoint, Method method,
            String... fqcns) throws Exception {
        List<SdkTypeMetaData> templates = new ArrayList<>();
        for (int i = 0; i < fqcns.length; i++) {
            templates.add(registry.createMetaData(fqcns[i], method.getParameters()[i]));
        }
        return new SdkTypeMiddleware(SdkTypeMiddlewareTest.class.getClassLoader(), templates, registry, endpoint);
    }

    private static SdkTypeMiddleware middleware(SdkTypeRegistry registry, Method method) throws Exception {
        return middleware(registry, null, method, BLOB_CLIENT);
    }

    private static void invoke(SdkTypeMiddleware middleware, ExecutionContextDataSource context) {
        try {
            middleware.invoke(context, next -> { });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static ExecutionContextDataSource context(Method method, List<ParameterBinding> inputs,
            WorkerObjectCache<CacheKey> cache) {
        BindingDataStore store = new BindingDataStore();
        store.setBindingDefinitions(Collections.emptyMap());
        store.addParameterSources(inputs);
        try (MockedStatic<WorkerLogManager> logs = mockStatic(WorkerLogManager.class)) {
            logs.when(() -> WorkerLogManager.getInvocationLogger("id")).thenReturn(Logger.getAnonymousLogger());
            ExecutionContextDataSource context = new ExecutionContextDataSource("id", null, null, "function", store,
                    new MethodBindInfo(method), SdkTypeMiddlewareTest.class, inputs, null, cache);
            store.addExecutionContextSource(context);
            return context;
        }
    }

    private static List<ParameterBinding> blob(String container, String name) {
        String json = "{\"Connection\":\"AzureWebJobsStorage\",\"ContainerName\":\"" + container
                + "\",\"BlobName\":\"" + name + "\"}";
        return Collections.singletonList(ParameterBinding.newBuilder().setName("blob").setData(
                TypedData.newBuilder().setModelBindingData(ModelBindingData.newBuilder()
                        .setContentType("application/json")
                        .setContent(ByteString.copyFromUtf8(json))))
                .build());
    }

    private static ModelBindingData envelope(byte[] content) {
        return ModelBindingData.newBuilder().setVersion("1.0").setSource("AzureServiceBusReceivedMessage")
                .setContentType("application/octet-stream").setContent(ByteString.copyFrom(content)).build();
    }

    private static ParameterBinding single(String name, byte[] content) {
        return ParameterBinding.newBuilder().setName(name)
                .setData(TypedData.newBuilder().setModelBindingData(envelope(content))).build();
    }

    /** Builds the hydrator result from the context it is given, so tests can inspect what the worker passed. */
    private static final class CapturingRegistry extends SdkTypeRegistry {
        final List<SdkInvocationContext> contexts = new CopyOnWriteArrayList<>();

        @Override
        public SdkType<?> createSdkType(SdkTypeMetaData metaData) {
            return new SdkType<SdkTypeMetaData>() {
                @Override
                public SdkTypeMetaData getMetaData() {
                    return metaData;
                }

                @Override
                public SdkTypeHydrator<SdkTypeMetaData> getHydrator() {
                    return new SdkTypeHydrator<SdkTypeMetaData>() {
                        @Override
                        public Object createInstance(SdkTypeMetaData ignored) {
                            throw new IllegalStateException("A context is required");
                        }

                        @Override
                        public Object createInstance(SdkTypeMetaData ignored, SdkInvocationContext context) {
                            contexts.add(context);
                            return ServiceBusSdkTypeFactory.ACTIONS.equals(metaData.getFqcn())
                                    ? new FakeActions() : new FakeMessage();
                        }
                    };
                }
            };
        }
    }

    public static class FakeMessage {
    }

    public static class FakeActions {
    }

    private static final AtomicReference<Object[]> CAPTURED_ARGUMENTS = new AtomicReference<>();

    public static void serviceBusFunction(
            @ServiceBusQueueTrigger(name = "message", queueName = "q", connection = "c") FakeMessage message,
            FakeActions actions) {
        CAPTURED_ARGUMENTS.set(new Object[] {message, actions});
    }

    public static void serviceBusBatchFunction(
            @ServiceBusQueueTrigger(name = "messages", queueName = "q", connection = "c") List<FakeMessage> messages) {
    }

    public void trigger(@BlobTrigger(name = "blob", path = "photos/{name}", connection = "AzureWebJobsStorage")
                        Object blob) {
    }
}
