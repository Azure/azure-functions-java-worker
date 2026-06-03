package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.Test;

public class HttpInvocationCoordinatorTest {

    private static final String INVOCATION_ID = "abc-123";

    @Test
    public void httpArrivesBeforeGrpc() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();

        CompletableFuture<InvocationRequest> grpcFuture = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertFalse(grpcFuture.isDone(), "gRPC future should still be pending before gRPC arrival");

        CompletableFuture<HttpExchange> httpFuture = coordinator.registerGrpcArrival(request);
        assertTrue(httpFuture.isDone(), "HTTP future should already be resolved once gRPC arrives");
        assertSame(exchange, httpFuture.get(1, TimeUnit.SECONDS));
        assertSame(request, grpcFuture.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void grpcArrivesBeforeHttp() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();

        CompletableFuture<HttpExchange> httpFuture = coordinator.registerGrpcArrival(request);
        assertFalse(httpFuture.isDone(), "HTTP future should still be pending before HTTP arrival");

        CompletableFuture<InvocationRequest> grpcFuture = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertTrue(grpcFuture.isDone(), "gRPC future should already be resolved once HTTP arrives");
        assertSame(exchange, httpFuture.get(1, TimeUnit.SECONDS));
        assertSame(request, grpcFuture.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void releaseInvocationRemovesSlot() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertEquals(1, coordinator.activeInvocationCount());

        coordinator.releaseInvocation(INVOCATION_ID);
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void releaseInvocationIsIdempotent() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        // Releasing an unknown invocation does not throw.
        coordinator.releaseInvocation("unknown");
        coordinator.releaseInvocation("unknown");
    }

    @Test
    public void failInvocationPropagatesToFutures() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        CompletableFuture<InvocationRequest> grpcFuture = coordinator.registerHttpArrival(INVOCATION_ID, exchange);

        IOException cause = new IOException("boom");
        coordinator.failInvocation(INVOCATION_ID, cause);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> grpcFuture.get(1, TimeUnit.SECONDS));
        assertSame(cause, ex.getCause());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void duplicateHttpArrivalThrows() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertThrows(IllegalStateException.class,
            () -> coordinator.registerHttpArrival(INVOCATION_ID, exchange));
    }

    @Test
    public void duplicateGrpcArrivalThrows() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();
        coordinator.registerGrpcArrival(request);
        assertThrows(IllegalStateException.class,
            () -> coordinator.registerGrpcArrival(request));
    }

    @Test
    public void independentInvocationsDoNotInterfere() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchangeA = mock(HttpExchange.class);
        HttpExchange exchangeB = mock(HttpExchange.class);
        InvocationRequest reqA = InvocationRequest.newBuilder().setInvocationId("a").build();
        InvocationRequest reqB = InvocationRequest.newBuilder().setInvocationId("b").build();

        CompletableFuture<InvocationRequest> grpcA = coordinator.registerHttpArrival("a", exchangeA);
        CompletableFuture<InvocationRequest> grpcB = coordinator.registerHttpArrival("b", exchangeB);
        // Resolve only A; B must still be pending.
        coordinator.registerGrpcArrival(reqA);
        assertTrue(grpcA.isDone());
        assertFalse(grpcB.isDone());

        coordinator.registerGrpcArrival(reqB);
        assertSame(reqA, grpcA.get(1, TimeUnit.SECONDS));
        assertSame(reqB, grpcB.get(1, TimeUnit.SECONDS));
    }

    @Test
    public void grpcFutureRemainsPendingUntilHttpArrives() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();
        CompletableFuture<HttpExchange> httpFuture = coordinator.registerGrpcArrival(request);
        // No HTTP arrival; future must time out.
        assertThrows(TimeoutException.class, () -> httpFuture.get(50, TimeUnit.MILLISECONDS));
    }
}
