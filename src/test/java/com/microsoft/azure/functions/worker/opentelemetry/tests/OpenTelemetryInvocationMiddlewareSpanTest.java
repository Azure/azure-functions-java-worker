package com.microsoft.azure.functions.worker.opentelemetry.tests;

import com.microsoft.azure.functions.worker.chain.OpenTelemetryInvocationMiddleware;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OpenTelemetryInvocationMiddlewareSpanTest {

    private static InMemorySpanExporter exporter;
    private static OpenTelemetrySdk sdk;

    @BeforeAll
    static void setUp() throws Exception {
        exporter = InMemorySpanExporter.create();

        // 1. create a provider that exports to the in-memory exporter
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .setResource(Resource.getDefault())
                .build();

        sdk = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .setPropagators(ContextPropagators.create(
                        TextMapPropagator.composite(
                                W3CTraceContextPropagator.getInstance(),
                                W3CBaggagePropagator.getInstance())))
                .build();

        // 2. overwrite the static-final field 'sdk' in the middleware
        Field sdkField = OpenTelemetryInvocationMiddleware.class.getDeclaredField("OPEN_TELEMETRY_SDK");
        sdkField.setAccessible(true);

        // remove the FINAL modifier bits
        Field mods = Field.class.getDeclaredField("modifiers");
        mods.setAccessible(true);
        mods.setInt(sdkField, sdkField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);

        sdkField.set(null, sdk);   // succeeds now

        // 3. also register globally (optional but harmless)
        GlobalOpenTelemetry.set(sdk);
    }

    @AfterEach
    void clear() { exporter.reset(); }

    @Test
    void spanIsCreatedAndEnriched() throws Exception {

        // --- build TraceContext --------------------
        String tp = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        ExecutionTraceContext trace =
                new ExecutionTraceContext(tp, "", new HashMap<>());

        // --- mock ExecutionContextDataSource -------
        ExecutionContextDataSource ctx = mock(ExecutionContextDataSource.class);
        when(ctx.getTraceContext()).thenReturn(trace);
        when(ctx.getInvocationId()).thenReturn("invocation-456");
        when(ctx.getFunctionName()).thenReturn("Hello");

        // spy chain
        MiddlewareChain chain = mock(MiddlewareChain.class);

        // --- invoke middleware ---------------------
        new OpenTelemetryInvocationMiddleware().invoke(ctx, chain);

        // --- assertions ----------------------------
        List<io.opentelemetry.sdk.trace.data.SpanData> spans = exporter.getFinishedSpanItems();
        assertEquals(1, spans.size(), "exactly one span ended");

        io.opentelemetry.sdk.trace.data.SpanData sd = spans.get(0);

        assertEquals("Hello", sd.getName());
        assertEquals(SpanKind.INTERNAL, sd.getKind()); // adjust if you switch to SERVER
        assertTrue(sd.getAttributes().get(AttributeKey.stringKey("faas.invocation_id"))
                .equals("invocation-456"));

        // parent trace Id must match the one from traceparent
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", sd.getTraceId());
    }
}
