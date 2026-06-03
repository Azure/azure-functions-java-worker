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
 * handler thread and the gRPC dispatcher thread wait on. The {@code completion}
 * future is signaled once the invocation has fully responded, allowing the slot
 * to be released from the coordinator's map.</p>
 *
 * <p>Instances are package-private; use {@link HttpInvocationCoordinator} to
 * acquire and release slots.</p>
 */
final class HttpInvocationSlot {
    private final String invocationId;
    private final CompletableFuture<HttpExchange> httpArrival = new CompletableFuture<>();
    private final CompletableFuture<InvocationRequest> grpcArrival = new CompletableFuture<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();

    HttpInvocationSlot(String invocationId) {
        this.invocationId = invocationId;
    }

    String getInvocationId() {
        return invocationId;
    }

    CompletableFuture<HttpExchange> httpArrival() {
        return httpArrival;
    }

    CompletableFuture<InvocationRequest> grpcArrival() {
        return grpcArrival;
    }

    CompletableFuture<Void> completion() {
        return completion;
    }
}
