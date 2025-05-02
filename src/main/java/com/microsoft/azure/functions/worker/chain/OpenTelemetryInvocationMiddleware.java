package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.worker.opentelemetry.FunctionsResourceDetector;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

import java.util.logging.Logger;

/**
 * Middleware that starts a Span for every function invocation,
 * using the trace context from the host if available.
 */
public class OpenTelemetryInvocationMiddleware implements Middleware {

    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();

    private static final TextMapGetter<TraceContext> GETTER =
            new TextMapGetter<TraceContext>() {
                public Iterable<String> keys(TraceContext c) {
                    return c.getAttributes().keySet();
                }

                public String get(TraceContext c, String k) {
                    if (c == null) return null;
                    if ("traceparent".equalsIgnoreCase(k)) return c.getTraceparent();
                    if ("tracestate".equalsIgnoreCase(k)) return c.getTracestate();
                    return c.getAttributes().get(k);
                }
            };

    private static final OpenTelemetrySdk sdk =
            AutoConfiguredOpenTelemetrySdk.builder()
                    .addResourceCustomizer(
                            (existing, unused) -> existing.merge(FunctionsResourceDetector.getResource()))
                    .build().getOpenTelemetrySdk();

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        if (!(context instanceof ExecutionContextDataSource)) {
            chain.doNext(context);
            return;
        }

        Context parent = sdk.getPropagators().getTextMapPropagator()
                .extract(Context.current(), context.getTraceContext(), GETTER);

        Span span = sdk.getTracer("func.app").spanBuilder(context.getFunctionName())
                .setParent(parent)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try {
            try (Scope scope = span.makeCurrent()) {
                span.setAttribute("faas.invocation_id", context.getInvocationId());
                chain.doNext(context);
            }
        } catch (Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            span.end();
            LOGGER.info("OpenTelemetryInvocationMiddleware ended span for " + context.getFunctionName());
        }
    }
}
