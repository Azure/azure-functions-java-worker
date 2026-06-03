package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.Test;

public class HttpProxyHandlerTest {

    private static final String INVOCATION_ID = "abc-123";

    @Test
    public void rejectsRequestWithoutInvocationIdHeader() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpProxyHandler handler = new HttpProxyHandler(coordinator);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getRequestHeaders()).thenReturn(new Headers());
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        handler.handle(exchange);

        verify(exchange).sendResponseHeaders(400, captured.size());
        verify(exchange).close();
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void registersHttpArrivalAndWaitsForCompletion() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpProxyHandler handler = new HttpProxyHandler(coordinator);
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        requestHeaders.add(HttpProxyHandler.INVOCATION_ID_HEADER, INVOCATION_ID);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);

        AtomicReference<Throwable> handlerError = new AtomicReference<>();
        CompletableFuture<Void> handlerDone = CompletableFuture.runAsync(() -> {
            try {
                handler.handle(exchange);
            } catch (Throwable t) {
                handlerError.set(t);
            }
        });

        // Wait for the handler to register HTTP arrival.
        long deadline = System.currentTimeMillis() + 1000;
        while (coordinator.activeInvocationCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, coordinator.activeInvocationCount());

        // Simulate the gRPC side finishing the invocation.
        coordinator.releaseInvocation(INVOCATION_ID);

        handlerDone.get();
        assertEquals(null, handlerError.get());
        verify(exchange).close();
        // The handler must NOT have written any error response - the gRPC side owns the body.
        verify(exchange, never()).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    public void respondsWith500WhenInvocationFails() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpProxyHandler handler = new HttpProxyHandler(coordinator);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        Headers requestHeaders = new Headers();
        requestHeaders.add(HttpProxyHandler.INVOCATION_ID_HEADER, INVOCATION_ID);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        CompletableFuture<Void> handlerDone = CompletableFuture.runAsync(() -> {
            try {
                handler.handle(exchange);
            } catch (Exception ignored) {
            }
        });

        long deadline = System.currentTimeMillis() + 1000;
        while (coordinator.activeInvocationCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        coordinator.failInvocation(INVOCATION_ID, new RuntimeException("user fn crashed"));

        handlerDone.get();
        verify(exchange).sendResponseHeaders(500, captured.size());
        verify(exchange).close();
        assertTrue(new String(captured.toByteArray()).contains("user fn crashed"));
    }

    @Test
    public void duplicateRegistrationReturns409() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpProxyHandler handler = new HttpProxyHandler(coordinator);
        // Pre-register HTTP arrival to force a duplicate on the next handle() call.
        HttpExchange first = mock(HttpExchange.class);
        coordinator.registerHttpArrival(INVOCATION_ID, first);

        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange second = mock(HttpExchange.class);
        Headers headers = new Headers();
        headers.add(HttpProxyHandler.INVOCATION_ID_HEADER, INVOCATION_ID);
        when(second.getRequestHeaders()).thenReturn(headers);
        when(second.getResponseHeaders()).thenReturn(new Headers());
        when(second.getResponseBody()).thenReturn(captured);

        handler.handle(second);

        verify(second).sendResponseHeaders(409, captured.size());
        verify(second).close();
    }
}
