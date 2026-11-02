package com.microsoft.azure.functions.worker.broker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.annotation.BlobTrigger;
import com.microsoft.azure.functions.cache.CacheKey;
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
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.chain.SdkTypeMiddleware;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    private static SdkTypeMiddleware middleware(SdkTypeRegistry registry, Method method) throws Exception {
        Parameter parameter = method.getParameters()[0];
        SdkTypeMetaData template = registry.createMetaData(BLOB_CLIENT, parameter);
        return new SdkTypeMiddleware(SdkTypeMiddlewareTest.class.getClassLoader(),
                Collections.singletonList(template), registry);
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
            return new ExecutionContextDataSource("id", null, null, "function", store, new MethodBindInfo(method),
                    SdkTypeMiddlewareTest.class, inputs, null, cache);
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

    public void trigger(@BlobTrigger(name = "blob", path = "photos/{name}", connection = "AzureWebJobsStorage")
                        Object blob) {
    }
}
