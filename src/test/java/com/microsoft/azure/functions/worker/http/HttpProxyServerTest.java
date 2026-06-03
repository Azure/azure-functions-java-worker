package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpHandler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class HttpProxyServerTest {

    private HttpProxyServer server;

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.close();
            server = null;
        }
    }

    @Test
    public void startBindsToEphemeralPortAndReturnsUri() throws Exception {
        server = new HttpProxyServer(ProxyConfig.defaults());
        String uri = server.start(noOpHandler());
        assertNotNull(uri);
        assertTrue(uri.startsWith("http://127.0.0.1:"), "Expected loopback URI, got " + uri);
        URI parsed = URI.create(uri);
        assertTrue(parsed.getPort() > 0, "Expected a real port number, got " + parsed.getPort());
        assertEquals(uri, server.getBoundUri());
    }

    @Test
    public void getBoundUriReturnsNullBeforeStart() {
        server = new HttpProxyServer(ProxyConfig.defaults());
        assertNull(server.getBoundUri());
    }

    @Test
    public void closeBeforeStartIsNoop() {
        server = new HttpProxyServer(ProxyConfig.defaults());
        server.close();
        assertNull(server.getBoundUri());
    }

    @Test
    public void doubleStartThrows() throws Exception {
        server = new HttpProxyServer(ProxyConfig.defaults());
        server.start(noOpHandler());
        assertThrows(IllegalStateException.class, () -> server.start(noOpHandler()));
    }

    @Test
    public void routesIncomingRequestToHandler() throws Exception {
        server = new HttpProxyServer(ProxyConfig.defaults());
        AtomicReference<String> seenPath = new AtomicReference<>();
        AtomicReference<String> seenHeader = new AtomicReference<>();
        String uri = server.start(exchange -> {
            seenPath.set(exchange.getRequestURI().getPath());
            seenHeader.set(exchange.getRequestHeaders().getFirst("x-ms-invocation-id"));
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        HttpURLConnection conn = (HttpURLConnection) URI.create(uri + "/some/route").toURL().openConnection();
        conn.setRequestProperty("x-ms-invocation-id", "test-123");
        conn.connect();
        try {
            assertEquals(200, conn.getResponseCode());
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                assertEquals("hello", reader.readLine());
            }
        } finally {
            conn.disconnect();
        }
        assertEquals("/some/route", seenPath.get());
        assertEquals("test-123", seenHeader.get());
    }

    @Test
    public void closeStopsAcceptingConnections() throws Exception {
        server = new HttpProxyServer(ProxyConfig.defaults());
        String uri = server.start(noOpHandler());
        server.close();
        server = null;
        HttpURLConnection conn = (HttpURLConnection) URI.create(uri + "/").toURL().openConnection();
        conn.setConnectTimeout(500);
        conn.setReadTimeout(500);
        // After close, the next connect attempt must fail (connection refused).
        assertThrows(Exception.class, conn::connect);
    }

    private static HttpHandler noOpHandler() {
        return exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        };
    }
}
