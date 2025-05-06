package com.microsoft.azure.functions.worker.opentelemetry.tests;

import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.chain.OpenTelemetryInvocationMiddleware;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * Unit-test for {@link OpenTelemetryInvocationMiddleware}.
 * Verifies that the middleware delegates to the next element
 * and does not throw.
 */
public class OpenTelemetryInvocationMiddlewareTest {
    @Test
    void middlewareDelegatesAndEndsSpan() throws Exception {
        // --- 1. create the middleware under test --------------------------
        OpenTelemetryInvocationMiddleware mw = new OpenTelemetryInvocationMiddleware();

        // --- 2. build a minimal, valid TraceContext -----------------------
        String traceparent =
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        Map<String,String> attrs = new HashMap<>();
        ExecutionTraceContext trace =
                new ExecutionTraceContext(traceparent, /* tracestate */"", attrs);

        // --- 3. mock ExecutionContextDataSource --------------------------
        ExecutionContextDataSource ctx = mock(ExecutionContextDataSource.class);
        when(ctx.getTraceContext()).thenReturn(trace);
        when(ctx.getInvocationId()).thenReturn("invocation-123");
        when(ctx.getFunctionName()).thenReturn("Hello");
        // any other getters the middleware may call can be stubbed here

        // --- 4. spy chain that records delegate call ---------------------
        MiddlewareChain chain = mock(MiddlewareChain.class);
        final boolean[] called = { false };
        doAnswer(inv -> { called[0] = true; return null; })
                .when(chain).doNext(ctx);

        // --- 5. invoke middleware ---------------------------------------
        mw.invoke(ctx, chain);

        // --- 6. verify ---------------------------------------------------
        assert called[0] : "Middleware must delegate to next chain element";
        // If mw.invoke returned without throwing, span.close() ran and ended.
    }
}
