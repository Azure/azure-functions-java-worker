package com.microsoft.azure.functions.worker.http;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.sun.net.httpserver.HttpExchange;

/**
 * Synchronizes the two halves of an HTTP-proxied invocation by invocation id.
 *
 * <p>The Functions host dispatches each HTTP-triggered invocation as two
 * messages that arrive on independent channels:</p>
 * <ol>
 *   <li>An HTTP request forwarded to the worker's embedded proxy server
 *       (delivered to a worker thread inside the JDK HttpServer pool).</li>
 *   <li>A gRPC {@code InvocationRequest} carrying trigger metadata and
 *       the matching {@code invocationId}.</li>
 * </ol>
 *
 * <p>This coordinator owns the per-invocation slot, exposes rendezvous methods
 * that block until the other half arrives, and releases the slot when the
 * invocation completes. Slot lookup and creation are atomic so the two halves
 * can race without losing one another.</p>
 *
 * <p>The coordinator does not impose timeouts: the host owns end-to-end
 * timeout enforcement, and per-invocation hangs are observable via the worker's
 * existing health and watchdog telemetry.</p>
 */
public final class HttpInvocationCoordinator {
    private final ConcurrentMap<String, HttpInvocationSlot> slots = new ConcurrentHashMap<>();

    /**
     * Registers the arrival of an HTTP request for the given invocation.
     * Returns the slot so the HTTP handler can await
     * {@link HttpInvocationSlot#completion()}.
     *
     * @throws IllegalStateException if HTTP arrival was already registered for this id
     */
    public HttpInvocationSlot registerHttpArrival(String invocationId, HttpExchange exchange) {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(exchange, "exchange");
        HttpInvocationSlot slot = slots.computeIfAbsent(invocationId, HttpInvocationSlot::new);
        if (!slot.httpArrival().complete(exchange)) {
            throw new IllegalStateException(
                "HTTP arrival already registered for invocation " + invocationId);
        }
        return slot;
    }

    /**
     * Registers the arrival of a gRPC InvocationRequest for the given invocation.
     * Returns the slot so the gRPC dispatcher can await
     * {@link HttpInvocationSlot#httpArrival()}.
     *
     * @throws IllegalStateException if gRPC arrival was already registered for this id
     */
    public HttpInvocationSlot registerGrpcArrival(InvocationRequest request) {
        Objects.requireNonNull(request, "request");
        String invocationId = request.getInvocationId();
        HttpInvocationSlot slot = slots.computeIfAbsent(invocationId, HttpInvocationSlot::new);
        if (!slot.grpcArrival().complete(request)) {
            throw new IllegalStateException(
                "gRPC arrival already registered for invocation " + invocationId);
        }
        return slot;
    }

    /**
     * Marks the invocation as complete and removes its slot. Idempotent. Any
     * outstanding rendezvous futures are cancelled to unblock callers.
     */
    public void releaseInvocation(String invocationId) {
        Objects.requireNonNull(invocationId, "invocationId");
        HttpInvocationSlot slot = slots.remove(invocationId);
        if (slot == null) {
            return;
        }
        slot.httpArrival().cancel(false);
        slot.grpcArrival().cancel(false);
        slot.completion().complete(null);
    }

    /**
     * Fails the invocation slot with the given throwable. Used when the worker
     * decides to abort an in-flight invocation (e.g., HTTP handler exception
     * before the user function runs). The slot is removed after failure.
     */
    public void failInvocation(String invocationId, Throwable cause) {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(cause, "cause");
        HttpInvocationSlot slot = slots.remove(invocationId);
        if (slot == null) {
            return;
        }
        slot.httpArrival().completeExceptionally(cause);
        slot.grpcArrival().completeExceptionally(cause);
        slot.completion().completeExceptionally(cause);
    }

    /** Visible for tests. */
    int activeInvocationCount() {
        return slots.size();
    }
}
