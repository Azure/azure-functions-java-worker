package com.microsoft.azure.functions.worker.chain;


import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareChain;
import com.microsoft.azure.functions.internal.spi.middleware.MiddlewareContext;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.logging.Logger;

/**
 * Middleware that starts a Span for every function invocation,
 * using the trace context from the host if available.
 */
public class OpenTelemetryInvocationMiddleware implements Middleware {

    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer("com.mycompany.myworker");

    // Basic text-map getter to read "traceparent" from environment or from the request
    private static final TextMapGetter<ExecutionTraceContext> GETTER = new TextMapGetter<ExecutionTraceContext>() {
        @Override
        public Iterable<String> keys(ExecutionTraceContext carrier) {
            if (carrier == null || carrier.getAttributes() == null) {
                return java.util.Collections.emptyList();
            }
            return carrier.getAttributes().keySet();
        }

        @Override
        public String get(ExecutionTraceContext carrier, String key) {
            switch (key) {
                case "traceparent":
                    return carrier.getTraceparent();
                case "tracestate":
                    return carrier.getTracestate();
                default:
                    return carrier.getAttributes().get(key);
            }
        }
    };

    @Override
    public void invoke(MiddlewareContext context, MiddlewareChain chain) throws Exception {
        if (!(context instanceof ExecutionContextDataSource)) {
            chain.doNext(context);
            return;
        }

        ExecutionContextDataSource execCtx = (ExecutionContextDataSource) context;
        ExecutionTraceContext traceCtx = (ExecutionTraceContext) execCtx.getTraceContext();

         //1. Extract parent context from the invocation's trace headers
         //   If not present, we get a root context
        Context parentOtelContext = io.opentelemetry.api.GlobalOpenTelemetry.getPropagators()
                .getTextMapPropagator()
                .extract(Context.current(), traceCtx, GETTER);

        // 2. Start a new Span for the function invocation
        Span span = tracer.spanBuilder(execCtx.getFunctionName())
                .setParent(parentOtelContext)
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try {
            // Make the span current
            try (Scope scope = span.makeCurrent()) {
                // 3. Log some example attribute on the span
                span.setAttribute("faas.invocation_id", execCtx.getInvocationId());

                // 4. Run the rest of the chain (this eventually calls user code)
                chain.doNext(context);
            }
        } catch (Throwable t) {
            // If the function threw an exception, record it on the span
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
            throw t;
        } finally {
            // 5. End the span
            span.end();
            LOGGER.info("OpenTelemetryInvocationMiddleware ended span for " + execCtx.getFunctionName());
            //LOGGER.info(span.toString());
        }
    }
}
