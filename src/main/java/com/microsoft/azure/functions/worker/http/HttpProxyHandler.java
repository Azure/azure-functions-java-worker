package com.microsoft.azure.functions.worker.http;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handler attached to the worker's embedded HTTP proxy server.
 *
 * <p>Receives HTTP requests forwarded by the Functions host (via the HttpUri
 * capability) and parks them on the {@link HttpInvocationCoordinator} until
 * the gRPC dispatcher picks them up. The actual invocation runs on the gRPC
 * dispatch thread, which reads the request body and writes the response back
 * to the same {@link HttpExchange}. This handler simply:</p>
 * <ol>
 *   <li>Extracts {@code x-ms-invocation-id} from the request headers.</li>
 *   <li>Registers the HTTP arrival with the coordinator.</li>
 *   <li>Blocks on the slot's {@code completion} future so the exchange stays
 *       open until the gRPC side finishes writing the response.</li>
 *   <li>Returns from {@code handle()}, letting the JDK HttpServer close the
 *       exchange.</li>
 * </ol>
 *
 * <p>Missing header or unexpected failures are converted into appropriate HTTP
 * error responses so the host always gets a closed connection.</p>
 */
public final class HttpProxyHandler implements HttpHandler {
    /** Header set by {@code DefaultHttpProxyService} on the host side. */
    public static final String INVOCATION_ID_HEADER = "x-ms-invocation-id";

    private static final Logger LOGGER = WorkerLogManager.getSystemLogger();

    private final HttpInvocationCoordinator coordinator;

    public HttpProxyHandler(HttpInvocationCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String invocationId = exchange.getRequestHeaders().getFirst(INVOCATION_ID_HEADER);
        if (invocationId == null || invocationId.isEmpty()) {
            LOGGER.warning("HTTP proxy request missing " + INVOCATION_ID_HEADER + " header");
            try {
                HttpBodyBridge.writeErrorResponse(exchange, 400,
                    "Missing required header: " + INVOCATION_ID_HEADER);
            } finally {
                exchange.close();
            }
            return;
        }

        HttpInvocationSlot slot;
        try {
            slot = coordinator.registerHttpArrival(invocationId, exchange);
        } catch (IllegalStateException ex) {
            LOGGER.log(Level.WARNING, "Duplicate HTTP arrival for invocation " + invocationId, ex);
            try {
                HttpBodyBridge.writeErrorResponse(exchange, 409,
                    "Duplicate HTTP arrival for invocation " + invocationId);
            } finally {
                exchange.close();
            }
            return;
        }

        try {
            // Block until the gRPC dispatcher signals invocation completion.
            // The dispatcher is responsible for writing the response to this
            // exchange; we simply hold the connection open in the meantime.
            slot.completion().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            tryWriteError(exchange, 503, "Worker interrupted while waiting for invocation");
        } catch (CancellationException ex) {
            // Coordinator cancelled the futures via releaseInvocation();
            // the gRPC side has already written (or chosen not to write) the response.
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            LOGGER.log(Level.WARNING, "Invocation " + invocationId + " failed before responding", cause);
            tryWriteError(exchange, 500, "Invocation failed: " + cause.getMessage());
        } finally {
            exchange.close();
        }
    }

    private static void tryWriteError(HttpExchange exchange, int status, String message) {
        try {
            HttpBodyBridge.writeErrorResponse(exchange, status, message);
        } catch (IOException ioe) {
            LOGGER.log(Level.FINE, "Unable to write error response (response likely already started)", ioe);
        }
    }
}
