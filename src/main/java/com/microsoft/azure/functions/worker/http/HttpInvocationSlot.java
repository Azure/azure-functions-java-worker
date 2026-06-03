package com.microsoft.azure.functions.worker.http;

import java.util.concurrent.CompletableFuture;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.sun.net.httpserver.HttpExchange;

/**
 * Holds the rendezvous state for a single in-flight HTTP-proxied invocation.
 *
 * <p>The Functions host delivers an invocation along two independent paths:</p>
 * <ul>
 *   <li>An HTTP request forwarded to the worker's proxy server, carrying the
 *       request body and headers.</li>
 *   <li>A gRPC {@code InvocationRequest} carrying trigger metadata, route
 *       parameters, and the {@code invocationId} used to correlate the two.</li>
 * </ul>
 *
 * <p>Either side may arrive first. The slot exposes futures that the HTTP
 * handler thread and the gRPC dispatcher thread wait on. The {@link #completion}
 * future is signaled once the invocation has fully responded, allowing the HTTP
 * handler to return from {@code handle()} so the server can close the exchange.</p>
 *
 * <p>The class is mutable from the coordinator's perspective only; consumers
 * see immutable {@link CompletableFuture} handles and use them to await
 * rendezvous and completion.</p>
 */
public final class HttpInvocationSlot {
    private final String invocationId;
    private final CompletableFuture<HttpExchange> httpArrival = new CompletableFuture<>();
    private final CompletableFuture<InvocationRequest> grpcArrival = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    HttpInvocationSlot(String invocationId) {
        this.invocationId = invocationId;
    }

    public String getInvocationId() {
        return invocationId;
    }

    /**
     * Future that resolves when the HTTP request for this invocation arrives.
     * Consumed by the gRPC dispatcher thread.
     */
    public CompletableFuture<HttpExchange> httpArrival() {
        return httpArrival;
    }

    /**
     * Future that resolves when the gRPC {@code InvocationRequest} for this
     * invocation arrives. Consumed by the HTTP handler thread.
     */
    public CompletableFuture<InvocationRequest> grpcArrival() {
        return grpcArrival;
    }

    /**
     * Future that resolves when the invocation has fully completed (response
     * written to HTTP, output bindings collected for the gRPC response).
     * The HTTP handler thread waits on this before returning from {@code handle()}.
     */
    public CompletableFuture<Void> completion() {
        return completion;
    }
}
