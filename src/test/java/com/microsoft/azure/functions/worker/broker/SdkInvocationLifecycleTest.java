package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.WorkerSdkInvocationContext;
import com.microsoft.azure.functions.worker.chain.InvocationChainFactory;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SdkInvocationLifecycleTest {
    private MockedStatic<WorkerLogManager> logs;

    @BeforeEach
    void configureInvocationLogger() {
        logs = mockStatic(WorkerLogManager.class);
        logs.when(() -> WorkerLogManager.getInvocationLogger("invocation")).thenReturn(Logger.getAnonymousLogger());
    }

    @AfterEach
    void releaseInvocationLogger() {
        logs.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void contextRemainsActiveThroughEntireChainThenCloses(boolean enabled) throws Exception {
        AtomicReference<WorkerSdkInvocationContext> captured = new AtomicReference<>();
        Middleware outer = (context, chain) -> {
            WorkerSdkInvocationContext sdk = ((ExecutionContextDataSource) context).getSdkInvocationContext();
            captured.set(sdk);
            assertTrue(sdk.isActive());
            chain.doNext(context);
            assertTrue(sdk.isActive(), "scope must include outer middleware after function execution");
        };
        Middleware function = (context, chain) -> assertTrue(captured.get().isActive());
        JavaFunctionBroker broker = broker(enabled, outer, function);
        broker.invokeMethod("function", request(), new ArrayList<>());
        assertEquals("invocation", captured.get().getInvocationId());
        assertEquals(URI.create("http://localhost:1234"), captured.get().getHostEndpoint());
        assertFalse(captured.get().isActive());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void contextClosesOnHydrationFunctionAndOuterMiddlewareFailure(int failingStage) throws Exception {
        AtomicReference<WorkerSdkInvocationContext> captured = new AtomicReference<>();
        IllegalStateException expected = new IllegalStateException("failure");
        Middleware outer = (context, chain) -> {
            captured.set(((ExecutionContextDataSource) context).getSdkInvocationContext());
            assertTrue(captured.get().isActive());
            if (failingStage == 0) {
                throw expected;
            }
            chain.doNext(context);
            assertTrue(captured.get().isActive());
            if (failingStage == 2) {
                throw expected;
            }
        };
        Middleware function = (context, chain) -> {
            if (failingStage == 1) {
                throw expected;
            }
        };
        JavaFunctionBroker broker = broker(true, outer, function);
        assertSame(expected, assertThrows(IllegalStateException.class,
                () -> broker.invokeMethod("function", request(), new ArrayList<>())));
        assertFalse(captured.get().isActive());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void cleanupFailureIsSurfacedWithoutReplacingInvocationFailure(boolean functionFails) throws Exception {
        IllegalStateException cleanup = new IllegalStateException("cleanup");
        IllegalArgumentException invocation = new IllegalArgumentException("invocation");
        AtomicReference<WorkerSdkInvocationContext> captured = new AtomicReference<>();
        Middleware function = (context, chain) -> {
            WorkerSdkInvocationContext sdk = ((ExecutionContextDataSource) context).getSdkInvocationContext();
            captured.set(sdk);
            sdk.registerCloseable(() -> { throw cleanup; });
            if (functionFails) {
                throw invocation;
            }
        };
        JavaFunctionBroker broker = broker(true, function);
        Exception actual = assertThrows(Exception.class,
                () -> broker.invokeMethod("function", request(), new ArrayList<>()));
        assertSame(functionFails ? invocation : cleanup, actual);
        if (functionFails) {
            assertArrayEquals(new Throwable[] {cleanup}, actual.getSuppressed());
        }
        assertFalse(captured.get().isActive());
    }

    @SuppressWarnings("unchecked")
    private static JavaFunctionBroker broker(boolean enabled, Middleware... middlewares) throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(mock(ClassLoaderProvider.class),
                URI.create("http://localhost:1234"));
        FunctionDefinition definition = mock(FunctionDefinition.class);
        when(definition.getCandidate()).thenReturn(new MethodBindInfo(
                SdkInvocationLifecycleTest.class.getDeclaredMethod("function")));
        when(definition.getBindingDefinitions()).thenReturn(Collections.emptyMap());
        doReturn(SdkInvocationLifecycleTest.class).when(definition).getContainingClass();
        Field methods = JavaFunctionBroker.class.getDeclaredField("methods");
        methods.setAccessible(true);
        ((Map<String, ImmutablePair<String, FunctionDefinition>>) methods.get(broker))
                .put("function", new ImmutablePair<>("function", definition));
        Field enabledField = JavaFunctionBroker.class.getDeclaredField("cachedSdkTypesEnabled");
        enabledField.setAccessible(true);
        enabledField.set(broker, enabled);
        InvocationChainFactory factory = new InvocationChainFactory(Arrays.asList(middlewares));
        if (enabled) {
            Field factories = JavaFunctionBroker.class.getDeclaredField("functionFactories");
            factories.setAccessible(true);
            ((Map<String, InvocationChainFactory>) factories.get(broker)).put("function", factory);
        } else {
            Field defaultFactory = JavaFunctionBroker.class.getDeclaredField("invocationChainFactory");
            defaultFactory.setAccessible(true);
            defaultFactory.set(broker, factory);
        }
        return broker;
    }

    private static InvocationRequest request() {
        return InvocationRequest.newBuilder().setInvocationId("invocation").build();
    }

    public static void function() {
    }
}
