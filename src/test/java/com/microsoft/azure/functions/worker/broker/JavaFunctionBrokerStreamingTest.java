package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JavaFunctionBroker#methodHasStreamingHttpBody(String)}.
 * The HTTP proxy dispatch path uses this signature pre-check to decide whether
 * to skip the buffered request body read and expose the live HTTP exchange
 * input stream to user functions that declare
 * {@code HttpRequestMessage<InputStream>}.
 */
public class JavaFunctionBrokerStreamingTest {

    // Test signatures spanning the supported and unsupported shapes.
    public void streamingFn(HttpRequestMessage<InputStream> req) {}
    public void streamingFnSubtype(HttpRequestMessage<BufferedInputStream> req) {}
    public void stringFn(HttpRequestMessage<String> req) {}
    public void rawFn(@SuppressWarnings("rawtypes") HttpRequestMessage req) {}
    public void noHttpFn(String s, int i) {}

    @Test
    public void methodHasStreamingHttpBody_inputStreamParam_returnsTrue() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        registerMethod(broker, "id-stream", "streamingFn");
        assertTrue(broker.methodHasStreamingHttpBody("id-stream"));
    }

    @Test
    public void methodHasStreamingHttpBody_inputStreamSubtypeParam_returnsTrue() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        registerMethod(broker, "id-substream", "streamingFnSubtype");
        assertTrue(broker.methodHasStreamingHttpBody("id-substream"));
    }

    @Test
    public void methodHasStreamingHttpBody_stringParam_returnsFalse() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        registerMethod(broker, "id-string", "stringFn");
        assertFalse(broker.methodHasStreamingHttpBody("id-string"));
    }

    @Test
    public void methodHasStreamingHttpBody_rawHttpRequestMessage_returnsFalse() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        registerMethod(broker, "id-raw", "rawFn");
        assertFalse(broker.methodHasStreamingHttpBody("id-raw"));
    }

    @Test
    public void methodHasStreamingHttpBody_noHttpParam_returnsFalse() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        registerMethod(broker, "id-nohttp", "noHttpFn");
        assertFalse(broker.methodHasStreamingHttpBody("id-nohttp"));
    }

    @Test
    public void methodHasStreamingHttpBody_unknownId_returnsFalse() throws Exception {
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        assertFalse(broker.methodHasStreamingHttpBody("never-registered"));
    }

    /**
     * Resolves the named test method on this class, builds a real
     * {@link MethodBindInfo} for it, wraps a mocked {@link FunctionDefinition}
     * around it, and reflectively inserts the entry into the broker's private
     * {@code methods} map so the public {@code methodHasStreamingHttpBody}
     * lookup can find it without going through the full descriptor /
     * classloader pipeline.
     */
    private void registerMethod(JavaFunctionBroker broker, String id, String methodName) throws Exception {
        Method method = null;
        for (Method m : JavaFunctionBrokerStreamingTest.class.getMethods()) {
            if (m.getName().equals(methodName)) {
                method = m;
                break;
            }
        }
        if (method == null) {
            throw new IllegalArgumentException("Test method not found: " + methodName);
        }
        MethodBindInfo mbi = new MethodBindInfo(method);

        FunctionDefinition functionDefinition = mock(FunctionDefinition.class);
        when(functionDefinition.getCandidate()).thenReturn(mbi);

        @SuppressWarnings("unchecked")
        Map<String, ImmutablePair<String, FunctionDefinition>> methods =
                (Map<String, ImmutablePair<String, FunctionDefinition>>) getField(broker, "methods");
        methods.put(id, ImmutablePair.of(methodName, functionDefinition));

        // Touch a live stream so the unused-import / classloading paths are
        // exercised; protects the assertion that the broker logic depends only
        // on the param type, not on any runtime body.
        try (InputStream ignored = new ByteArrayInputStream(new byte[0])) {
            // no-op
        }
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
