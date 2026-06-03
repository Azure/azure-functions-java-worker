package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.IOException;
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

        HttpInvocationSlot httpSlot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertFalse(httpSlot.grpcArrival().isDone(), "gRPC future should still be pending before gRPC arrival");

        HttpInvocationSlot grpcSlot = coordinator.registerGrpcArrival(request);
        assertSame(httpSlot, grpcSlot, "both registrations should yield the same slot");
        assertTrue(grpcSlot.httpArrival().isDone(), "HTTP future should already be resolved once gRPC arrives");
        assertSame(exchange, grpcSlot.httpArrival().get(1, TimeUnit.SECONDS));
        assertSame(request, httpSlot.grpcArrival().get(1, TimeUnit.SECONDS));
    }

    @Test
    public void grpcArrivesBeforeHttp() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();

        HttpInvocationSlot grpcSlot = coordinator.registerGrpcArrival(request);
        assertFalse(grpcSlot.httpArrival().isDone(), "HTTP future should still be pending before HTTP arrival");

        HttpInvocationSlot httpSlot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertSame(grpcSlot, httpSlot, "both registrations should yield the same slot");
        assertTrue(httpSlot.grpcArrival().isDone(), "gRPC future should already be resolved once HTTP arrives");
        assertSame(exchange, httpSlot.httpArrival().get(1, TimeUnit.SECONDS));
        assertSame(request, httpSlot.grpcArrival().get(1, TimeUnit.SECONDS));
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
        HttpInvocationSlot slot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);

        IOException cause = new IOException("boom");
        coordinator.failInvocation(INVOCATION_ID, cause);

        ExecutionException ex = assertThrows(ExecutionException.class,
            () -> slot.grpcArrival().get(1, TimeUnit.SECONDS));
        assertSame(cause, ex.getCause());
        ExecutionException completionEx = assertThrows(ExecutionException.class,
            () -> slot.completion().get(1, TimeUnit.SECONDS));
        assertSame(cause, completionEx.getCause());
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

        HttpInvocationSlot slotA = coordinator.registerHttpArrival("a", exchangeA);
        HttpInvocationSlot slotB = coordinator.registerHttpArrival("b", exchangeB);
        // Resolve only A; B must still be pending.
        coordinator.registerGrpcArrival(reqA);
        assertTrue(slotA.grpcArrival().isDone());
        assertFalse(slotB.grpcArrival().isDone());

        coordinator.registerGrpcArrival(reqB);
        assertSame(reqA, slotA.grpcArrival().get(1, TimeUnit.SECONDS));
        assertSame(reqB, slotB.grpcArrival().get(1, TimeUnit.SECONDS));
    }

    @Test
    public void grpcFutureRemainsPendingUntilHttpArrives() {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        InvocationRequest request = InvocationRequest.newBuilder().setInvocationId(INVOCATION_ID).build();
        HttpInvocationSlot slot = coordinator.registerGrpcArrival(request);
        // No HTTP arrival; future must time out.
        assertThrows(TimeoutException.class, () -> slot.httpArrival().get(50, TimeUnit.MILLISECONDS));
    }

    @Test
    public void completionFutureResolvesOnRelease() throws Exception {
        HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();
        HttpExchange exchange = mock(HttpExchange.class);
        HttpInvocationSlot slot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertFalse(slot.completion().isDone());
        coordinator.releaseInvocation(INVOCATION_ID);
        slot.completion().get(1, TimeUnit.SECONDS); // resolves without throwing
    }
}
