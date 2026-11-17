package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.annotation.BlobInput;
import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeDescriptor;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.sdktype.blob.BlobClientDescriptor;
import com.microsoft.azure.functions.sdktype.blob.BlobClientHydrator;
import com.microsoft.azure.functions.sdktype.blob.BlobClientMetaData;
import com.microsoft.azure.functions.sdktype.blob.BlobClientSdkType;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the shared SdkTypeMetaData race.
 *
 * One SdkTypeMiddleware serves every invocation of a function, and invocations run concurrently.
 * Each invocation must therefore bind its own immutable metadata from the shared descriptor. If the
 * metadata is shared and mutable, one invocation can overwrite the blob name of another between
 * building the cache key and building the client, which hands one caller the client of a different
 * blob and caches it under the wrong key.
 */
public class SdkTypeMiddlewareTest {

    private static final String BLOB_CLIENT_FQCN = "com.azure.storage.blob.BlobClient";
    private static final String PARAM_NAME = "blobInput";
    private static final String CONTAINER = "container";
    private static final String CONNECTION = "AzureWebJobsStorage";

    @SuppressWarnings("unused")
    private static void blobFunction(@BlobInput(name = PARAM_NAME, path = "container/{name}") String blob) {
    }

    private static Parameter blobParameter() throws Exception {
        return SdkTypeMiddlewareTest.class.getDeclaredMethod("blobFunction", String.class).getParameters()[0];
    }

    @Test
    public void concurrentInvocationBuildsAndCachesClientFromItsOwnMetadata() throws Exception {
        GatedCache cache = new GatedCache();
        FakeRegistry registry = new FakeRegistry(true);
        SdkTypeMiddleware middleware = middleware(registry.mock, blobClientDescriptor());
        Invocation invocationA = new Invocation("blob-A", cache);
        Invocation invocationB = new Invocation("blob-B", cache);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Invocation A stops after its cache key is built and before its client is created.
            Future<?> a = executor.submit(invocationA.runWith(middleware));
            assertTrue(cache.reached.await(10, TimeUnit.SECONDS), "invocation A never reached the cache");

            // Invocation B runs to completion while A is parked at that exact point.
            invocationB.run(middleware);

            cache.release.countDown();
            a.get(10, TimeUnit.SECONDS);
        } finally {
            cache.release.countDown();
            executor.shutdownNow();
        }

        assertEquals("blob-A", ((FakeClient) invocationA.result()).blobName);
        assertEquals("blob-B", ((FakeClient) invocationB.result()).blobName);
        assertSame(invocationA.result(), cache.get(SdkTypeMiddleware.class, expectedKey("blob-A")));
        assertSame(invocationB.result(), cache.get(SdkTypeMiddleware.class, expectedKey("blob-B")));
    }

    @Test
    public void nonCachableTypeIsBuiltFromEachInvocationsOwnMetadata() throws Exception {
        FakeRegistry registry = new FakeRegistry(false);
        SdkTypeMiddleware middleware = middleware(registry.mock, blobClientDescriptor());
        WorkerObjectCache<CacheKey> cache = new WorkerObjectCache<>();
        Invocation first = new Invocation("blob-1", cache);
        Invocation second = new Invocation("blob-2", cache);

        first.run(middleware);
        second.run(middleware);

        assertEquals("blob-1", ((FakeClient) first.result()).blobName);
        assertEquals("blob-2", ((FakeClient) second.result()).blobName);
        assertNotSame(registry.created.get(0), registry.created.get(1));
        assertNull(cache.get(SdkTypeMiddleware.class, expectedKey("blob-1")));
    }

    @Test
    public void missingRequiredValueNamesTheParameterAndKey() throws Exception {
        SdkTypeMiddleware middleware = middleware(new FakeRegistry(true).mock, blobClientDescriptor());
        Invocation invocation = new Invocation("blob-A", new WorkerObjectCache<>());
        invocation.values.remove("BlobName");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> invocation.run(middleware));

        assertTrue(ex.getMessage().contains(PARAM_NAME), ex.getMessage());
        assertTrue(ex.getMessage().contains("BlobName"), ex.getMessage());
    }

    private SdkTypeMiddleware middleware(SdkTypeRegistry registry, SdkTypeDescriptor descriptor) {
        return new SdkTypeMiddleware(
                getClass().getClassLoader(), Collections.singletonList(descriptor), registry);
    }

    private BlobClientDescriptor blobClientDescriptor() throws Exception {
        return new BlobClientDescriptor(BLOB_CLIENT_FQCN, blobParameter());
    }

    private CacheKey expectedKey(String blobName) throws Exception {
        BlobClientMetaData metaData =
                new BlobClientMetaData(BLOB_CLIENT_FQCN, blobParameter(), CONTAINER, blobName, CONNECTION);
        return new BlobClientSdkType(metaData, mock(BlobClientHydrator.class)).buildCacheKey();
    }

    /** One simulated function invocation with its own binding data, sharing the given cache. */
    private static final class Invocation {
        final String blobName;
        final Map<String, Object> values = new HashMap<>();
        private final ExecutionContextDataSource context = mock(ExecutionContextDataSource.class);
        private volatile Object result;

        Invocation(String blobName, WorkerObjectCache<CacheKey> cache) {
            this.blobName = blobName;
            values.put("ContainerName", CONTAINER);
            values.put("BlobName", blobName);
            values.put("Connection", CONNECTION);

            BindingDataStore store = mock(BindingDataStore.class);
            when(store.getDataByNameFromInputSource(anyString(), any(), anyString())).thenAnswer(invocation -> {
                Object value = values.get(invocation.<String>getArgument(0));
                return value == null ? Optional.empty() : Optional.of(new BindingData(value));
            });
            when(context.getDataStore()).thenReturn(store);
            when(context.getCache()).thenReturn(cache);
            when(context.getFunctionName()).thenReturn("blobFunction");
            when(context.getInvocationId()).thenReturn("invocation-" + blobName);
            doAnswer(invocation -> {
                result = invocation.getArgument(1);
                return null;
            }).when(context).updateParameterValue(anyString(), any());
        }

        void run(SdkTypeMiddleware middleware) throws Exception {
            middleware.invoke(context, mock(MiddlewareChain.class));
        }

        Callable<Void> runWith(SdkTypeMiddleware middleware) {
            return () -> {
                run(middleware);
                return null;
            };
        }

        Object result() {
            assertNotNull(result, "no client was bound for " + blobName);
            return result;
        }
    }

    private static final class FakeClient {
        final String blobName;

        FakeClient(String blobName) {
            this.blobName = blobName;
        }
    }

    /** Stands in for the real hydrator, which would need a live storage connection. */
    private static BlobClientHydrator fakeHydrator() throws Exception {
        BlobClientHydrator hydrator = mock(BlobClientHydrator.class);
        when(hydrator.createInstance(any(BlobClientMetaData.class)))
                .thenAnswer(invocation -> new FakeClient(invocation.<BlobClientMetaData>getArgument(0).getBlobName()));
        return hydrator;
    }

    private static final class NonCachableBlobClientSdkType implements SdkType<BlobClientMetaData> {
        private final BlobClientMetaData metaData;

        NonCachableBlobClientSdkType(BlobClientMetaData metaData) {
            this.metaData = metaData;
        }

        @Override
        public BlobClientMetaData getMetaData() {
            return metaData;
        }

        @Override
        public SdkTypeHydrator<BlobClientMetaData> getHydrator() {
            return m -> new FakeClient(m.getBlobName());
        }
    }

    private static final class FakeRegistry {
        final List<SdkTypeMetaData> created = Collections.synchronizedList(new ArrayList<SdkTypeMetaData>());
        final SdkTypeRegistry mock = mock(SdkTypeRegistry.class);

        FakeRegistry(boolean cachable) throws Exception {
            when(mock.createSdkType(any(SdkTypeMetaData.class))).thenAnswer(invocation -> {
                SdkTypeMetaData metaData = invocation.getArgument(0);
                created.add(metaData);
                BlobClientMetaData blobMetaData = (BlobClientMetaData) metaData;
                return cachable
                        ? new BlobClientSdkType(blobMetaData, fakeHydrator())
                        : new NonCachableBlobClientSdkType(blobMetaData);
            });
        }
    }

    /** Parks the first caller after its cache key is built, until the test releases it. */
    private static final class GatedCache extends WorkerObjectCache<CacheKey> {
        final CountDownLatch reached = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean gated = new AtomicBoolean();

        @Override
        public Object computeIfAbsent(Class<?> namespace, CacheKey key, Supplier<Object> creator) {
            if (gated.compareAndSet(false, true)) {
                reached.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.computeIfAbsent(namespace, key, creator);
        }
    }
}
