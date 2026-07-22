package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression guard for issue #879.
 *
 * initializeOneTimeLogics() runs the double-checked locking pattern behind a volatile flag. The
 * injector must be built before that flag flips to true. If the flag is set first, a second load
 * thread can pass the lock-free outer check, skip initialization, and start invoking while
 * functionInstanceInjector is still null, which throws NPE in
 * ExecutionContextDataSource.getFunctionInstance().
 */
public class JavaFunctionBrokerConcurrentInitTest {

    @Test
    public void flagIsNotSetUntilInjectorIsInitialized() throws Exception {
        CountDownLatch reachedInjectorInit = new CountDownLatch(1);
        CountDownLatch releaseInjectorInit = new CountDownLatch(1);
        AtomicInteger createClassLoaderCalls = new AtomicInteger();

        // createClassLoader() is called once at the top of initializeOneTimeLogics() and once inside
        // initializeFunctionInstanceInjector(). Freeze the second call so the broker can be observed
        // at the exact point the injector is about to be built.
        ClassLoaderProvider classLoaderProvider = mock(ClassLoaderProvider.class);
        when(classLoaderProvider.createClassLoader()).thenAnswer(invocation -> {
            if (createClassLoaderCalls.incrementAndGet() == 2) {
                reachedInjectorInit.countDown();
                releaseInjectorInit.await(10, TimeUnit.SECONDS);
            }
            return Thread.currentThread().getContextClassLoader();
        });

        JavaFunctionBroker broker = new JavaFunctionBroker(classLoaderProvider);

        Method initializeOneTimeLogics = JavaFunctionBroker.class.getDeclaredMethod("initializeOneTimeLogics");
        initializeOneTimeLogics.setAccessible(true);
        Field oneTimeLogicInitialized = JavaFunctionBroker.class.getDeclaredField("oneTimeLogicInitialized");
        oneTimeLogicInitialized.setAccessible(true);
        Field functionInstanceInjector = JavaFunctionBroker.class.getDeclaredField("functionInstanceInjector");
        functionInstanceInjector.setAccessible(true);

        AtomicReference<Throwable> initFailure = new AtomicReference<>();
        Thread initThread = new Thread(() -> {
            try {
                initializeOneTimeLogics.invoke(broker);
            } catch (Throwable t) {
                initFailure.set(t);
            }
        }, "one-time-init");
        initThread.setDaemon(true);
        initThread.start();

        assertTrue(reachedInjectorInit.await(5, TimeUnit.SECONDS), "init thread never reached injector initialization");

        // The injector has not been built yet, so the flag must still be false. If it is already
        // true here, a concurrent thread would see an "initialized" broker with a null injector.
        assertNull(functionInstanceInjector.get(broker), "test precondition: injector should not be built yet");
        assertFalse((boolean) oneTimeLogicInitialized.get(broker),
                "oneTimeLogicInitialized was set before the injector was initialized (issue #879 regression)");

        releaseInjectorInit.countDown();
        initThread.join(TimeUnit.SECONDS.toMillis(5));

        assertNull(initFailure.get(), () -> "initializeOneTimeLogics threw: " + initFailure.get());
        assertFalse(initThread.isAlive(), "init thread did not finish");
        assertTrue((boolean) oneTimeLogicInitialized.get(broker), "flag should be set once initialization completes");
        assertNotNull(functionInstanceInjector.get(broker), "injector should be initialized once init completes");
        assertEquals(2, createClassLoaderCalls.get(), "createClassLoader hook did not match the injector init call");
    }
}
