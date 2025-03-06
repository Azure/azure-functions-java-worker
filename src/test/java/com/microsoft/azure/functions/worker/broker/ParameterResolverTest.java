package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.RpcException;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.ExecutionRetryContext;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.cache.WorkerObjectCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class ParameterResolverTest {

    private ExecutionContextDataSource executionContextDataSource;
    @Mock
    private MethodBindInfo methodBindInfo;

    @BeforeEach
    public void setup() {
        String invocationId = "testInvocationId";
        ExecutionTraceContext traceContext = new ExecutionTraceContext("traceParent", "traceState", new HashMap<>());
        ExecutionRetryContext retryContext = new ExecutionRetryContext(1, 2, RpcException.newBuilder().build());
        String functionName = "ParameterResolverTest";
        BindingDataStore dataStore = new BindingDataStore();
        dataStore.setBindingDefinitions(new HashMap<>());
        try (MockedStatic<WorkerLogManager> workerLogManagerMockedStatic = Mockito.mockStatic(WorkerLogManager.class)) {
            workerLogManagerMockedStatic.when(() -> WorkerLogManager.getInvocationLogger(invocationId))
                    .thenReturn(Logger.getAnonymousLogger());
            executionContextDataSource = new ExecutionContextDataSource(invocationId,
                    traceContext, retryContext, functionName, dataStore, methodBindInfo,
                    this.getClass(), new ArrayList<>(), new FunctionInstanceInjector() {
                @Override
                public <T> T getInstance(Class<T> functionClass) throws Exception {
                    return null;
                }
            }, new WorkerObjectCache<>());
        }

    }

    @Test
    public void testResolveArgumentsHasImplicitOutputTrue() throws Exception {
        Method testMethod = this.getClass().getDeclaredMethod("testMethodVoid");
        when(methodBindInfo.hasNonVoidReturnType()).thenCallRealMethod();
        when(methodBindInfo.hasEffectiveReturnType()).thenCallRealMethod();
        when(methodBindInfo.hasImplicitOutput()).thenReturn(true);
        when(methodBindInfo.getMethod()).thenReturn(testMethod);
        when(methodBindInfo.getParams()).thenReturn(new ArrayList<>());
        ParameterResolver.resolveArguments(executionContextDataSource);
        assertTrue(executionContextDataSource.getDataStore().getDataTargetTypedValue(BindingDataStore.RETURN_NAME).isPresent());
    }

    @Test
    public void testResolveArgumentsHasImplicitOutputFalse() throws Exception {
        Method testMethod = this.getClass().getDeclaredMethod("testMethodVoid");
        when(methodBindInfo.hasNonVoidReturnType()).thenCallRealMethod();
        when(methodBindInfo.hasEffectiveReturnType()).thenCallRealMethod();
        when(methodBindInfo.hasImplicitOutput()).thenReturn(false);
        when(methodBindInfo.getMethod()).thenReturn(testMethod);
        when(methodBindInfo.getParams()).thenReturn(new ArrayList<>());
        ParameterResolver.resolveArguments(executionContextDataSource);
        assertFalse(executionContextDataSource.getDataStore().getDataTargetTypedValue(BindingDataStore.RETURN_NAME).isPresent());
    }

    @Test
    public void testResolveArgumentsNonVoidReturnTypeTrue() throws Exception {
        Method testMethod = this.getClass().getDeclaredMethod("testMethodNonVoid");
        when(methodBindInfo.getMethod()).thenReturn(testMethod);
        when(methodBindInfo.getParams()).thenReturn(new ArrayList<>());
        when(methodBindInfo.hasNonVoidReturnType()).thenCallRealMethod(); // Ensures real logic is executed
        when(methodBindInfo.hasImplicitOutput()).thenReturn(false);
        when(methodBindInfo.hasEffectiveReturnType()).thenCallRealMethod();
        ParameterResolver.resolveArguments(executionContextDataSource);
        assertTrue(executionContextDataSource.getDataStore().getDataTargetTypedValue(BindingDataStore.RETURN_NAME).isPresent());
    }

    @Test
    public void testResolveArgumentsNonVoidReturnTypeFalse() throws Exception {
        Method testMethod = this.getClass().getDeclaredMethod("testMethodVoid");
        when(methodBindInfo.getMethod()).thenReturn(testMethod);
        when(methodBindInfo.getParams()).thenReturn(new ArrayList<>());
        when(methodBindInfo.hasNonVoidReturnType()).thenCallRealMethod(); // Ensures real logic is executed
        when(methodBindInfo.hasImplicitOutput()).thenReturn(false);
        when(methodBindInfo.hasEffectiveReturnType()).thenCallRealMethod();
        ParameterResolver.resolveArguments(executionContextDataSource);
        assertFalse(executionContextDataSource.getDataStore().getDataTargetTypedValue(BindingDataStore.RETURN_NAME).isPresent());
    }

    public void testMethodVoid() {}
    public String testMethodNonVoid() { return "test"; }
}
