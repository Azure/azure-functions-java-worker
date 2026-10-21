package com.microsoft.azure.functions.worker.http;

import java.util.concurrent.CompletableFuture;

import com.sun.net.httpserver.HttpExchange;

/**
 * Rendezvous state for a single in-flight HTTP-proxied invocation.
 *
 * <p>The Functions host delivers an invocation along two independent paths: an
 * HTTP request forwarded to the worker's proxy server, and a gRPC
 * {@code InvocationRequest} carrying trigger metadata. Both carry the same
 * invocation id. Either side may arrive first, so each looks the slot up by id
 * and the gRPC side waits on {@link #httpArrival()}.</p>
 *
 * <p>{@link #completion()} is signaled once the invocation has fully responded,
 * allowing the HTTP handler to return from {@code handle()} so the server can
 * close the exchange.</p>
 */
public final class HttpInvocationSlot {
    private final String invocationId;
    private final CompletableFuture<HttpExchange> httpArrival = new CompletableFuture<>();
    private final CompletableFuture<Void> invocationArrival = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    HttpInvocationSlot(String invocationId) {
        this.invocationId = invocationId;
    }

    public String getInvocationId() {
        return invocationId;
    }

    /** Resolves when the HTTP request for this invocation arrives. */
    public CompletableFuture<HttpExchange> httpArrival() {
        return httpArrival;
    }

    CompletableFuture<Void> invocationArrival() {
        return invocationArrival;
    }

    /**
     * Resolves when the invocation has fully completed (response written to
     * HTTP). The HTTP handler thread waits on this before returning.
     */
    public CompletableFuture<Void> completion() {
        return completion;
    }
}
