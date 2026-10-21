package com.microsoft.azure.functions.worker.http;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
 * <p>This coordinator owns the per-invocation slot and releases it when the
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
     * Returns the slot for the given invocation, creating it if the other half
     * of the invocation has not arrived yet. Lookup and creation are atomic.
     */
    public HttpInvocationSlot slotFor(String invocationId) {
        Objects.requireNonNull(invocationId, "invocationId");
        return slots.computeIfAbsent(invocationId, HttpInvocationSlot::new);
    }

    /**
     * Registers the arrival of an HTTP request for the given invocation.
     * Returns the slot so the HTTP handler can await
     * {@link HttpInvocationSlot#completion()}.
     *
     * @throws IllegalStateException if HTTP arrival was already registered for this id
     */
    public HttpInvocationSlot registerHttpArrival(String invocationId, HttpExchange exchange) {
        Objects.requireNonNull(exchange, "exchange");
        HttpInvocationSlot slot = slotFor(invocationId);
        if (!slot.httpArrival().complete(exchange)) {
            throw new IllegalStateException(
                "HTTP arrival already registered for invocation " + invocationId);
        }
        return slot;
    }

    /**
     * Marks the invocation as complete and removes its slot. Idempotent.
     */
    public void releaseInvocation(String invocationId) {
        Objects.requireNonNull(invocationId, "invocationId");
        HttpInvocationSlot slot = slots.remove(invocationId);
        if (slot == null) {
            return;
        }
        slot.httpArrival().cancel(false);
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
        slot.completion().completeExceptionally(cause);
    }

    /** Visible for tests. */
    public int activeInvocationCount() {
        return slots.size();
    }
}
