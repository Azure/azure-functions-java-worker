package com.microsoft.azure.functions.worker.handler;

import java.net.ServerSocket;

import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;
import com.microsoft.azure.functions.rpc.messages.WorkerInitResponse;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;
import com.microsoft.azure.functions.worker.http.ProxyConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class WorkerInitRequestHandlerTest {
    @Test
    public void advertisesHttpUriOnlyAfterSuccessfulStart() throws Exception {
        try (HttpProxyServer server = new HttpProxyServer(ProxyConfig.defaults(), new HttpInvocationCoordinator())) {
            WorkerInitResponse.Builder response = WorkerInitResponse.newBuilder();
            new WorkerInitRequestHandler(mock(JavaFunctionBroker.class), server)
                .execute(WorkerInitRequest.getDefaultInstance(), response);
            assertTrue(server.isRunning());
            assertEquals(server.getBoundUri(), response.getCapabilitiesOrThrow("HttpUri"));
            assertEquals("true", response.getCapabilitiesOrThrow("RequiresRouteParameters"));
        }
    }

    @Test
    public void bindingFailureKeepsLegacyCapabilitiesWithoutAdvertisingProxy() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0);
                HttpProxyServer server = new HttpProxyServer(
                    new ProxyConfig("127.0.0.1", occupied.getLocalPort()), new HttpInvocationCoordinator())) {
            WorkerInitResponse.Builder response = WorkerInitResponse.newBuilder();
            new WorkerInitRequestHandler(mock(JavaFunctionBroker.class), server)
                .execute(WorkerInitRequest.getDefaultInstance(), response);
            assertFalse(server.isRunning());
            assertFalse(response.containsCapabilities("HttpUri"));
            assertFalse(response.containsCapabilities("RequiresRouteParameters"));
            assertTrue(response.containsCapabilities("RpcHttpBodyOnly"));
        }
    }

    @Test
    public void disabledProxyDoesNotAdvertiseHttpUri() throws Exception {
        WorkerInitResponse.Builder response = WorkerInitResponse.newBuilder();
        new WorkerInitRequestHandler(mock(JavaFunctionBroker.class))
            .execute(WorkerInitRequest.getDefaultInstance(), response);
        assertFalse(response.containsCapabilities("HttpUri"));
    }
}
