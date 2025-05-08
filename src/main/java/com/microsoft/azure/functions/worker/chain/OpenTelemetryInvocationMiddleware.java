package com.microsoft.azure.functions.worker.chain;

import com.microsoft.azure.functions.worker.opentelemetry.FunctionsResourceDetector;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.WorkerLogManager;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
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

    /** Extracts W3C trace-parent information from {@link TraceContext}. */
    private static final TextMapGetter<TraceContext> TRACE_CONTEXT_GETTER =
            new TextMapGetter<TraceContext>() {
                @Override
                public Iterable<String> keys(TraceContext traceContext) {
                    return traceContext.getAttributes().keySet();
                }

                @Override
                public String get(TraceContext traceContext, String key) {
                    if (traceContext == null) {
                        return null;
                    }
                    if ("traceparent".equalsIgnoreCase(key)) {
                        return traceContext.getTraceparent();
                    }
                    if ("tracestate".equalsIgnoreCase(key)) {
                        return traceContext.getTracestate();
                    }
                    return traceContext.getAttributes().get(key);
                }
            };

    /** The SDK the worker uses for internal spans (exporter configured elsewhere). */
    private static final OpenTelemetrySdk OPEN_TELEMETRY_SDK =
            AutoConfiguredOpenTelemetrySdk.builder()
                    .addResourceCustomizer(
                            (existingResource, unused) ->
                                    existingResource.merge(FunctionsResourceDetector.getResource()))
                    .build()
                    .getOpenTelemetrySdk();

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        // Only act for real ExecutionContextDataSource instances
        if (!(context instanceof ExecutionContextDataSource)) {
            chain.doNext(context);
            return;
        }

        // 1) Re-hydrate the parent context from trace-parent headers
        Context parentContext = OPEN_TELEMETRY_SDK.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), context.getTraceContext(), TRACE_CONTEXT_GETTER);

        // 2) Create a span for this invocation
        Span invocationSpan = OPEN_TELEMETRY_SDK.getTracer("azure.functions.worker")
                .spanBuilder(context.getFunctionName())
                .setParent(parentContext)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (Scope ignored = invocationSpan.makeCurrent()) {
            invocationSpan.setAttribute("faas.invocation_id", context.getInvocationId());
            invocationSpan.setAttribute("faas.name", context.getFunctionName());

            // Delegate to the rest of the chain
            chain.doNext(context);

        } catch (Throwable throwable) {          // capture any user exception
            invocationSpan.recordException(throwable);
            invocationSpan.setStatus(StatusCode.ERROR, throwable.getMessage());
            throw throwable;                     // keep behaviour unchanged
        } finally {
            invocationSpan.end();
        }
    }
}
