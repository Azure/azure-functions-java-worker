package com.microsoft.azure.functions.worker.broker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.annotation.ServiceBusQueueTrigger;
import com.microsoft.azure.functions.cache.CacheKey;
import com.microsoft.azure.functions.internal.spi.sdk.SdkInvocationContext;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.sdktype.CachableSdkType;
import com.microsoft.azure.functions.sdktype.SdkType;
import com.microsoft.azure.functions.sdktype.SdkTypeHydrator;
import com.microsoft.azure.functions.sdktype.SdkTypeMetaData;
import com.microsoft.azure.functions.sdktype.SdkTypeRegistry;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import com.microsoft.azure.functions.worker.chain.SdkTypeMiddleware;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SdkTypeMiddlewareTest {
    @Test
    void sameFunctionConcurrentInvocationsUseFreshMetadata() throws Exception {
        Method method = getClass().getDeclaredMethod("trigger", Object.class);
        Parameter parameter = method.getParameters()[0];
        TestMetadata template = new TestMetadata(parameter);
        CyclicBarrier hydrated = new CyclicBarrier(2);
        SdkTypeRegistry registry = new SdkTypeRegistry() {
            @Override
            public SdkTypeMetaData createMetaData(String fqcn, Parameter param) {
                return new TestMetadata(param);
            }

            @Override
            public SdkType<?> createSdkType(SdkTypeMetaData metadata) {
                return new SdkType<SdkTypeMetaData>() {
                    public SdkTypeMetaData getMetaData() {
                        return metadata;
                    }

                    public SdkTypeHydrator<SdkTypeMetaData> getHydrator() {
                        return new SdkTypeHydrator<SdkTypeMetaData>() {
                            public Object createInstance(SdkTypeMetaData data) {
                                throw new AssertionError("Context-aware hydrator must be called");
                            }

                            public Object createInstance(SdkTypeMetaData data, SdkInvocationContext context)
                                    throws Exception {
                                hydrated.await(5, TimeUnit.SECONDS);
                                assertTrue(context.isActive());
                                return context.getInvocationId() + ":" + data.getFieldValue("ContainerName");
                            }
                        };
                    }
                };
            }
        };
        SdkTypeMiddleware middleware = new SdkTypeMiddleware(getClass().getClassLoader(),
                Collections.singletonList(template), registry);
        ExecutionContextDataSource first = context("first", method, json("one"), new WorkerObjectCache<>());
        ExecutionContextDataSource second = context("second", method, json("two"), new WorkerObjectCache<>());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> one = executor.submit(() -> invoke(middleware, first));
            Future<?> two = executor.submit(() -> invoke(middleware, second));
            one.get(10, TimeUnit.SECONDS);
            two.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals("first:one", first.getSdkParameterValue(parameter).get().getValue());
        assertEquals("second:two", second.getSdkParameterValue(parameter).get().getValue());
        assertNull(template.getFieldValue("ContainerName"), "analysis metadata must remain untouched");
    }

    @Test
    void legacyRequiredFieldsAndCacheKeysRemainEffective() throws Exception {
        Method method = getClass().getDeclaredMethod("trigger", Object.class);
        Parameter parameter = method.getParameters()[0];
        SdkTypeRegistry registry = mock(SdkTypeRegistry.class);
        when(registry.createMetaData(any(), any())).thenAnswer(call -> new TestMetadata(parameter));
        CachableSdkType<?> sdkType = mock(CachableSdkType.class);
        CacheKey key = mock(CacheKey.class);
        when(sdkType.buildCacheKey()).thenReturn(key);
        Object client = new Object();
        when(sdkType.buildInstance(any(SdkInvocationContext.class))).thenReturn(client);
        doReturn(sdkType).when(registry).createSdkType(any());
        SdkTypeMiddleware middleware = new SdkTypeMiddleware(getClass().getClassLoader(),
                Collections.singletonList(new TestMetadata(parameter)), registry);
        WorkerObjectCache<CacheKey> cache = new WorkerObjectCache<>();
        ExecutionContextDataSource first = context("one", method, json("photos"), cache);
        ExecutionContextDataSource second = context("two", method, json("photos"), cache);
        invoke(middleware, first);
        invoke(middleware, second);
        assertSame(client, first.getSdkParameterValue(parameter).get().getValue());
        assertSame(client, second.getSdkParameterValue(parameter).get().getValue());
        verify(sdkType, times(1)).buildInstance(any(SdkInvocationContext.class));
        verify(registry, times(2)).createMetaData("test.Sdk", parameter);
    }

    @Test
    void companionActionsAndTriggerUseIndependentParameterDestinations() throws Exception {
        Method method = getClass().getDeclaredMethod("withActions", Object.class, Object.class);
        Parameter messageParam = method.getParameters()[0];
        Parameter actionsParam = method.getParameters()[1];
        Object message = new Object();
        Object actions = new Object();
        ExecutionContextDataSource context = context("id", method, json("photos"), new WorkerObjectCache<>());
        SdkTypeRegistry registry = mock(SdkTypeRegistry.class);
        when(registry.createMetaData(any(), any())).thenAnswer(call -> new TestMetadata(call.getArgument(1)));
        when(registry.createSdkType(any())).thenAnswer(call -> {
            SdkType<?> sdk = mock(SdkType.class);
            SdkTypeMetaData metadata = call.getArgument(0);
            when(sdk.buildInstance(context.getSdkInvocationContext()))
                    .thenReturn(metadata.getParam().equals(messageParam) ? message : actions);
            return sdk;
        });
        SdkTypeMiddleware middleware = new SdkTypeMiddleware(getClass().getClassLoader(),
                Arrays.asList(new TestMetadata(messageParam), new TestMetadata(actionsParam)), registry);
        invoke(middleware, context);
        assertSame(message, context.getSdkParameterValue(messageParam).get().getValue());
        assertSame(actions, context.getSdkParameterValue(actionsParam).get().getValue());
        Object[] resolved = (Object[]) ParameterResolver.resolveArguments(context).get().invoke(() -> this);
        assertArrayEquals(new Object[] {message, actions}, resolved);
        assertSame(message, context.getBindingData("message", Object.class).get().getValue());
    }

    @Test
    void missingLegacyRequiredFieldFailsExplicitly() throws Exception {
        Method method = getClass().getDeclaredMethod("trigger", Object.class);
        Parameter parameter = method.getParameters()[0];
        SdkTypeRegistry registry = mock(SdkTypeRegistry.class);
        when(registry.createMetaData(any(), any())).thenReturn(new TestMetadata(parameter));
        SdkTypeMiddleware middleware = new SdkTypeMiddleware(getClass().getClassLoader(),
                Collections.singletonList(new TestMetadata(parameter)), registry);
        ExecutionContextDataSource context = context("missing", method, Collections.emptyList(),
                new WorkerObjectCache<>());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> middleware.invoke(context, next -> fail("Must not invoke user function")));
        assertTrue(error.getMessage().contains("ContainerName"));
        assertTrue(error.getMessage().contains("missing"));
        verify(registry, never()).createSdkType(any());
    }

    private static void invoke(SdkTypeMiddleware middleware, ExecutionContextDataSource context) {
        try {
            middleware.invoke(context, next -> { });
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static ExecutionContextDataSource context(String id, Method method,
            java.util.List<ParameterBinding> inputs, WorkerObjectCache<CacheKey> cache) {
        BindingDataStore store = new BindingDataStore();
        store.setBindingDefinitions(Collections.emptyMap());
        store.addParameterSources(inputs);
        try (MockedStatic<WorkerLogManager> logs = mockStatic(WorkerLogManager.class)) {
            logs.when(() -> WorkerLogManager.getInvocationLogger(id)).thenReturn(Logger.getAnonymousLogger());
            return new ExecutionContextDataSource(id, null, null, "function", store, new MethodBindInfo(method),
                    SdkTypeMiddlewareTest.class, inputs, null, cache);
        }
    }

    private static java.util.List<ParameterBinding> json(String container) {
        return Collections.singletonList(ParameterBinding.newBuilder().setName("message").setData(
                TypedData.newBuilder().setModelBindingData(
                        com.microsoft.azure.functions.rpc.messages.ModelBindingData.newBuilder()
                                .setContentType("application/json")
                                .setContent(ByteString.copyFromUtf8("{\"ContainerName\":\"" + container + "\"}"))))
                .build());
    }

    public void trigger(@ServiceBusQueueTrigger(name = "message", queueName = "queue", connection = "connection")
                        Object message) {
    }

    public Object[] withActions(
            @ServiceBusQueueTrigger(name = "message", queueName = "queue", connection = "connection") Object message,
            @BindingName("message") Object actions) {
        return new Object[] {message, actions};
    }

    private static class TestMetadata implements SdkTypeMetaData {
        private final Parameter parameter;
        private final Map<String, Object> values = new HashMap<>();

        TestMetadata(Parameter parameter) {
            this.parameter = parameter;
        }

        public Object getFieldValue(String field) {
            return values.get(field);
        }

        public void setFieldValue(String field, Object value) {
            values.put(field, value);
        }

        public void parseAndVerify() {
            assertNotNull(values.get("ContainerName"));
        }

        public Parameter getParam() {
            return parameter;
        }

        public String getFqcn() {
            return "test.Sdk";
        }

        public Set<String> getRequiredFields() {
            return Collections.singleton("ContainerName");
        }
    }
}
