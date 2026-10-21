package com.microsoft.azure.functions.worker.http;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
 * <p>The initial handoff is bounded so a missing transport cannot leak a slot
 * indefinitely. This is not a function execution or streaming duration limit.</p>
 */
public final class HttpInvocationCoordinator implements AutoCloseable {
    private final Map<String, HttpInvocationSlot> slots = new HashMap<>();
    private final long handoffTimeout;
    private final TimeUnit timeoutUnit;
    private boolean closed;

    public HttpInvocationCoordinator() {
        this(30, TimeUnit.SECONDS);
    }

    HttpInvocationCoordinator(long handoffTimeout, TimeUnit timeoutUnit) {
        this.handoffTimeout = handoffTimeout;
        this.timeoutUnit = Objects.requireNonNull(timeoutUnit, "timeoutUnit");
    }

    /**
     * Returns the slot for the given invocation, creating it if the other half
     * of the invocation has not arrived yet. Lookup and creation are atomic.
     */
    public synchronized HttpInvocationSlot slotFor(String invocationId) {
        Objects.requireNonNull(invocationId, "invocationId");
        if (closed) {
            throw new RejectedExecutionException("HTTP proxy is closed");
        }
        return slots.computeIfAbsent(invocationId, HttpInvocationSlot::new);
    }

    public HttpExchange awaitHttpArrival(String invocationId)
            throws InterruptedException, ExecutionException, TimeoutException {
        HttpInvocationSlot slot = slotFor(invocationId);
        if (!slot.invocationArrival().complete(null)) {
            throw new IllegalStateException("Invocation already registered: " + invocationId);
        }
        return slot.httpArrival().get(handoffTimeout, timeoutUnit);
    }

    void awaitInvocation(HttpInvocationSlot slot)
            throws InterruptedException, ExecutionException, TimeoutException {
        slot.invocationArrival().get(handoffTimeout, timeoutUnit);
    }

    /**
     * Registers the arrival of an HTTP request for the given invocation.
     * Returns the slot so the HTTP handler can await
     * {@link HttpInvocationSlot#completion()}.
     *
     * @throws IllegalStateException if HTTP arrival was already registered for this id
     */
    public synchronized HttpInvocationSlot registerHttpArrival(String invocationId, HttpExchange exchange) {
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
    public synchronized void releaseInvocation(String invocationId) {
        Objects.requireNonNull(invocationId, "invocationId");
        HttpInvocationSlot slot = slots.remove(invocationId);
        if (slot == null) {
            return;
        }
        slot.httpArrival().cancel(false);
        slot.invocationArrival().complete(null);
        slot.completion().complete(null);
    }

    /**
     * Fails the invocation slot with the given throwable. Used when the worker
     * decides to abort an in-flight invocation (e.g., HTTP handler exception
     * before the user function runs). The slot is removed after failure.
     */
    public synchronized void failInvocation(String invocationId, Throwable cause) {
        Objects.requireNonNull(invocationId, "invocationId");
        Objects.requireNonNull(cause, "cause");
        HttpInvocationSlot slot = slots.remove(invocationId);
        if (slot == null) {
            return;
        }
        slot.httpArrival().completeExceptionally(cause);
        slot.invocationArrival().completeExceptionally(cause);
        slot.completion().completeExceptionally(cause);
    }

    @Override
    public synchronized void close() {
        closed = true;
        CancellationException cause = new CancellationException("HTTP proxy is shutting down");
        for (HttpInvocationSlot slot : slots.values()) {
            slot.httpArrival().completeExceptionally(cause);
            slot.invocationArrival().completeExceptionally(cause);
            slot.completion().completeExceptionally(cause);
        }
        slots.clear();
    }

    /** Visible for tests. */
    public synchronized int activeInvocationCount() {
        return slots.size();
    }
}
