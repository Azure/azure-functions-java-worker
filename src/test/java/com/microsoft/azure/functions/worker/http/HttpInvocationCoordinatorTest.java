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

import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.Test;

public class HttpInvocationCoordinatorTest {

    private static final String INVOCATION_ID = "abc-123";

    private final HttpInvocationCoordinator coordinator = new HttpInvocationCoordinator();

    @Test
    public void httpArrivesBeforeInvocationLooksUpSlot() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);

        HttpInvocationSlot httpSlot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);

        HttpInvocationSlot invocationSlot = coordinator.slotFor(INVOCATION_ID);
        assertSame(httpSlot, invocationSlot, "both sides should share the same slot");
        assertSame(exchange, invocationSlot.httpArrival().get(1, TimeUnit.SECONDS));
    }

    @Test
    public void invocationLooksUpSlotBeforeHttpArrives() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);

        HttpInvocationSlot invocationSlot = coordinator.slotFor(INVOCATION_ID);
        assertFalse(invocationSlot.httpArrival().isDone(), "HTTP future should be pending before HTTP arrival");

        HttpInvocationSlot httpSlot = coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertSame(invocationSlot, httpSlot, "both sides should share the same slot");
        assertSame(exchange, invocationSlot.httpArrival().get(1, TimeUnit.SECONDS));
    }

    @Test
    public void httpFutureRemainsPendingUntilHttpArrives() {
        HttpInvocationSlot slot = coordinator.slotFor(INVOCATION_ID);
        assertThrows(TimeoutException.class, () -> slot.httpArrival().get(50, TimeUnit.MILLISECONDS));
    }

    @Test
    public void releaseInvocationRemovesSlot() {
        coordinator.registerHttpArrival(INVOCATION_ID, mock(HttpExchange.class));
        assertEquals(1, coordinator.activeInvocationCount());

        coordinator.releaseInvocation(INVOCATION_ID);
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void releaseInvocationIsIdempotent() {
        coordinator.releaseInvocation("unknown");
        coordinator.releaseInvocation("unknown");
    }

    @Test
    public void failInvocationPropagatesToFutures() {
        HttpInvocationSlot slot = coordinator.registerHttpArrival(INVOCATION_ID, mock(HttpExchange.class));

        IOException cause = new IOException("boom");
        coordinator.failInvocation(INVOCATION_ID, cause);

        ExecutionException completionEx = assertThrows(ExecutionException.class,
            () -> slot.completion().get(1, TimeUnit.SECONDS));
        assertSame(cause, completionEx.getCause());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void duplicateHttpArrivalThrows() {
        HttpExchange exchange = mock(HttpExchange.class);
        coordinator.registerHttpArrival(INVOCATION_ID, exchange);
        assertThrows(IllegalStateException.class,
            () -> coordinator.registerHttpArrival(INVOCATION_ID, exchange));
    }

    @Test
    public void independentInvocationsDoNotInterfere() {
        HttpInvocationSlot slotA = coordinator.registerHttpArrival("a", mock(HttpExchange.class));
        HttpInvocationSlot slotB = coordinator.slotFor("b");

        assertTrue(slotA.httpArrival().isDone());
        assertFalse(slotB.httpArrival().isDone());
    }

    @Test
    public void completionFutureResolvesOnRelease() throws Exception {
        HttpInvocationSlot slot = coordinator.registerHttpArrival(INVOCATION_ID, mock(HttpExchange.class));
        assertFalse(slot.completion().isDone());
        coordinator.releaseInvocation(INVOCATION_ID);
        slot.completion().get(1, TimeUnit.SECONDS);
    }
}
