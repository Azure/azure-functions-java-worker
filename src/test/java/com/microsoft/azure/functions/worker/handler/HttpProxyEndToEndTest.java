package com.microsoft.azure.functions.worker.handler;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.InvocationResponse;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.StreamingMessage;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.RpcHttpRequestDataSource;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker.HttpInvocationOutcome;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyHandler;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;
import com.microsoft.azure.functions.worker.http.ProxyConfig;
import com.sun.net.httpserver.HttpExchange;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration tests for the HTTP proxy dispatch path. These wire
 * the real {@link HttpProxyServer}, {@link HttpProxyHandler}, and
 * {@link HttpInvocationCoordinator} together with a real
 * {@link InvocationRequestHandler} backed by a mocked {@link JavaFunctionBroker}.
 *
 * <p>Each test sends an actual HTTP request through the embedded proxy on one
 * thread while simulating the gRPC {@code InvocationRequest} arrival on the
 * test thread, exercising the rendezvous and validating the full pipeline:
 * HTTP arrival, body propagation (buffered or streamed), broker invocation,
 * and response writeback (buffered or streamed).</p>
 *
 * <p>The mocked broker captures the {@link InvocationRequest} it sees and the
 * thread-local exchange that was active during {@code invokeMethodForHttpProxy}
 * so the tests can assert that the streaming pre-check and the body-read skip
 * behaved correctly.</p>
 */
public class HttpProxyEndToEndTest {

    private static final String INVOCATION_ID = "e2e-invocation-1";
    private static final String FUNCTION_ID = "e2e-function-1";
    private static final long HTTP_AWAIT_MS = 5_000;

    private HttpInvocationCoordinator coordinator;
    private HttpProxyServer server;
    private String proxyUri;
    private JavaFunctionBroker brokerMock;
    private InvocationRequestHandler handler;
    private MockedStatic<WorkerLogManager> workerLogManagerMock;

    @BeforeEach
    public void setUp() throws Exception {
        // WorkerLogManager.getInvocationLogger calls addHandlers which asserts
        // the singleton has been initialized with a JavaWorkerClient (only true
        // when the worker is running under the host). Mock the static façade
        // and forward everything else to the real implementation so system/host
        // loggers continue to work for diagnostic output during tests.
        workerLogManagerMock = Mockito.mockStatic(WorkerLogManager.class, Answers.CALLS_REAL_METHODS);
        workerLogManagerMock.when(() -> WorkerLogManager.getInvocationLogger(anyString()))
                .thenReturn(Logger.getAnonymousLogger());

        coordinator = new HttpInvocationCoordinator();
        server = new HttpProxyServer(ProxyConfig.defaults());
        proxyUri = server.start(new HttpProxyHandler(coordinator));
        brokerMock = mock(JavaFunctionBroker.class);
        when(brokerMock.getMethodName(anyString())).thenReturn(Optional.of("TestFn"));
        handler = new InvocationRequestHandler(brokerMock, coordinator);
    }

    @AfterEach
    public void tearDown() {
        if (server != null) {
            server.close();
        }
        if (workerLogManagerMock != null) {
            workerLogManagerMock.close();
        }
    }

    // -------- 1. Buffered request, buffered response (existing path) --------
    @Test
    public void bufferedRequest_bufferedResponse_roundTrips() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        AtomicReference<InvocationRequest> seenRequest = new AtomicReference<>();
        AtomicReference<HttpExchange> seenExchange = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    seenRequest.set(inv.getArgument(1));
                    seenExchange.set(RpcHttpRequestDataSource.currentExchange());
                    RpcHttp respHttp = RpcHttp.newBuilder()
                            .setStatusCode("200")
                            .putHeaders("Content-Type", "text/plain")
                            .setBody(TypedData.newBuilder().setString("pong").build())
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(respHttp).build();
                    return new HttpInvocationOutcome(Optional.of(returnValue), null);
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "POST", "/api/echo", "text/plain", "ping".getBytes(StandardCharsets.UTF_8));

        awaitHttpArrival();
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(200, resp.statusCode);
        assertEquals("pong", new String(resp.body, StandardCharsets.UTF_8));
        assertEquals("text/plain", resp.contentType);

        // The buffered path must have folded the HTTP body into the RpcHttp envelope.
        // For text/* content types the bridge stores the body as a string, not bytes
        // (mirrors host PopulateBody behavior).
        TypedData httpInput = seenRequest.get().getInputDataList().get(0).getData();
        assertEquals("ping", httpInput.getHttp().getBody().getString());
        // For non-streaming requests the per-thread exchange must NOT be set.
        assertNull(seenExchange.get(), "Buffered-input invocations must not install a thread-local exchange");
    }

    // -------- 2. Buffered request, streaming response (commit #5 path) --------
    @Test
    public void bufferedRequest_streamingInputStreamResponse_streamsBackUnbuffered() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        byte[] payload = repeat("stream-chunk-", 5000); // ~65KB to force multi-chunk write
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    RpcHttp envelope = RpcHttp.newBuilder()
                            .setStatusCode("200")
                            .putHeaders("Content-Type", "application/octet-stream")
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(envelope).build();
                    return new HttpInvocationOutcome(Optional.of(returnValue), new ByteArrayInputStream(payload));
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "GET", "/api/download", null, new byte[0]);

        awaitHttpArrival();
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(200, resp.statusCode);
        assertEquals("application/octet-stream", resp.contentType);
        assertEquals(payload.length, resp.body.length);
        assertTrue(java.util.Arrays.equals(payload, resp.body), "Streamed body must match payload byte-for-byte");
        // Chunked transfer encoding (server-side) is used when the body length is
        // unknown. HttpURLConnection transparently dechunks; we can verify
        // either header or fall-through behavior via the chunked flag.
        assertEquals("chunked", resp.transferEncoding,
                "Streaming-output path must use chunked transfer-encoding (no Content-Length)");
    }

    // -------- 3. Buffered request, streaming response via IOConsumer --------
    @Test
    public void bufferedRequest_streamingIOConsumerResponse_streamsBack() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        IOConsumer<OutputStream> writer = out -> {
            for (int i = 0; i < 100; i++) {
                out.write(("event: tick\ndata: " + i + "\n\n").getBytes(StandardCharsets.UTF_8));
            }
        };
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    RpcHttp envelope = RpcHttp.newBuilder()
                            .setStatusCode("200")
                            .putHeaders("Content-Type", "text/event-stream")
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(envelope).build();
                    return new HttpInvocationOutcome(Optional.of(returnValue), writer);
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "GET", "/api/sse", null, new byte[0]);

        awaitHttpArrival();
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(200, resp.statusCode);
        assertEquals("text/event-stream", resp.contentType);
        String body = new String(resp.body, StandardCharsets.UTF_8);
        assertTrue(body.startsWith("event: tick\ndata: 0\n\n"));
        assertTrue(body.endsWith("event: tick\ndata: 99\n\n"));
    }

    // -------- 4. Streaming request, buffered response (commit #6 path) --------
    @Test
    public void streamingRequest_bufferedResponse_skipsBodyReadAndExposesLiveStream() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(true);

        byte[] uploadPayload = repeat("upload-frag-", 4096); // ~50KB
        AtomicReference<byte[]> consumedByBroker = new AtomicReference<>();
        AtomicReference<InvocationRequest> seenRequest = new AtomicReference<>();
        AtomicReference<HttpExchange> seenExchange = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    seenRequest.set(inv.getArgument(1));
                    HttpExchange exch = RpcHttpRequestDataSource.currentExchange();
                    seenExchange.set(exch);
                    // Consume the live request body to validate the streaming path actually wired through.
                    if (exch != null) {
                        byte[] consumed = readAll(exch.getRequestBody());
                        consumedByBroker.set(consumed);
                    }
                    RpcHttp respHttp = RpcHttp.newBuilder()
                            .setStatusCode("201")
                            .putHeaders("Content-Type", "text/plain")
                            .setBody(TypedData.newBuilder().setString("ok").build())
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(respHttp).build();
                    return new HttpInvocationOutcome(Optional.of(returnValue), null);
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "POST", "/api/upload", "application/octet-stream", uploadPayload);

        awaitHttpArrival();
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(201, resp.statusCode);
        assertEquals("ok", new String(resp.body, StandardCharsets.UTF_8));

        // Critical: the streaming-input path must NOT have populated the protobuf body.
        TypedData httpInput = seenRequest.get().getInputDataList().get(0).getData();
        assertEquals(0, httpInput.getHttp().getBody().getBytes().size(),
                "Streaming-input request must leave the protobuf body untouched (empty)");
        assertNotNull(seenExchange.get(), "Streaming-input invocation must install the thread-local exchange");
        // The bytes the broker read off the live exchange must equal what the client sent.
        assertNotNull(consumedByBroker.get());
        assertTrue(java.util.Arrays.equals(uploadPayload, consumedByBroker.get()),
                "Bytes read from the live exchange must equal the bytes the client sent");
        // After the invocation completes, the thread-local must be cleared so the
        // worker thread can be safely reused.
        assertNull(RpcHttpRequestDataSource.currentExchange(),
                "InvocationRequestHandler must clear the thread-local exchange in its finally block");
    }

    // -------- 5. Full streaming: streaming request + streaming response --------
    @Test
    public void streamingRequest_streamingResponse_endToEnd() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(true);

        byte[] uploadPayload = repeat("full-stream-", 1024);
        AtomicReference<byte[]> echoedBack = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    HttpExchange exch = RpcHttpRequestDataSource.currentExchange();
                    byte[] consumed = readAll(exch.getRequestBody());
                    echoedBack.set(consumed);
                    RpcHttp envelope = RpcHttp.newBuilder()
                            .setStatusCode("200")
                            .putHeaders("Content-Type", "application/octet-stream")
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(envelope).build();
                    // Echo what we read back to the client as a streaming response.
                    return new HttpInvocationOutcome(Optional.of(returnValue), new ByteArrayInputStream(consumed));
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "POST", "/api/echo-stream", "application/octet-stream", uploadPayload);

        awaitHttpArrival();
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(200, resp.statusCode);
        assertTrue(java.util.Arrays.equals(uploadPayload, echoedBack.get()),
                "Bytes broker read off live stream must equal client's uploaded payload");
        assertTrue(java.util.Arrays.equals(uploadPayload, resp.body),
                "Streaming response body must equal what the broker echoed");
    }

    // -------- 6. Coordinator slot is released after success --------
    @Test
    public void coordinatorSlotReleasedAfterSuccessfulInvocation() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    RpcHttp respHttp = RpcHttp.newBuilder()
                            .setStatusCode("204")
                            .build();
                    TypedData returnValue = TypedData.newBuilder().setHttp(respHttp).build();
                    return new HttpInvocationOutcome(Optional.of(returnValue), null);
                });

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "DELETE", "/api/item", null, new byte[0]);

        awaitHttpArrival();
        assertEquals(1, coordinator.activeInvocationCount());
        runGrpcArrival();

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);
        assertEquals(204, resp.statusCode);

        // The slot must be released so the coordinator can serve subsequent invocations.
        assertEquals(0, coordinator.activeInvocationCount(),
                "Coordinator must release the slot after a successful invocation");
    }

    // -------- 7. Broker exception surfaces as a 500 on the HTTP side --------
    @Test
    public void brokerExceptionPropagatesAsHttp500() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any()))
                .thenThrow(new RuntimeException("user function blew up"));

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "GET", "/api/boom", null, new byte[0]);

        awaitHttpArrival();
        // The gRPC-side execute throws; handle() catches it. We invoke directly so
        // the caller (this test) doesn't need to deal with gRPC marshalling.
        try {
            runGrpcArrival();
        } catch (Exception ignored) {
            // Expected: the broker's RuntimeException propagates out of execute().
        }

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);
        assertEquals(500, resp.statusCode);
        assertTrue(new String(resp.body, StandardCharsets.UTF_8).contains("user function blew up"),
                "500 body must surface the underlying failure message");
    }

    // -------- Helpers --------

    private void awaitHttpArrival() throws InterruptedException {
        long deadline = System.currentTimeMillis() + HTTP_AWAIT_MS;
        while (coordinator.activeInvocationCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(1, coordinator.activeInvocationCount(),
                "HTTP arrival should have registered with the coordinator by now");
    }

    private void runGrpcArrival() throws Exception {
        InvocationRequest request = buildInvocationRequest();
        InvocationResponse.Builder response = InvocationResponse.newBuilder();
        handler.execute(request, response);
    }

    private static InvocationRequest buildInvocationRequest() {
        // Trigger metadata only; body is empty (the host's HTTP proxy contract).
        RpcHttp httpEnvelope = RpcHttp.newBuilder()
                .setMethod("POST")
                .setUrl("http://localhost/api/test")
                .build();
        TypedData inputData = TypedData.newBuilder().setHttp(httpEnvelope).build();
        ParameterBinding binding = ParameterBinding.newBuilder()
                .setName("req")
                .setData(inputData)
                .build();
        return InvocationRequest.newBuilder()
                .setInvocationId(INVOCATION_ID)
                .setFunctionId(FUNCTION_ID)
                .addInputData(binding)
                .build();
    }

    private CompletableFuture<HttpClientResult> sendHttpRequestAsync(
            String method, String path, String contentType, byte[] body) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) URI.create(proxyUri + path).toURL().openConnection();
                conn.setRequestMethod(method);
                conn.setRequestProperty(HttpProxyHandler.INVOCATION_ID_HEADER, INVOCATION_ID);
                if (contentType != null) {
                    conn.setRequestProperty("Content-Type", contentType);
                }
                if (body != null && body.length > 0) {
                    conn.setDoOutput(true);
                    conn.setFixedLengthStreamingMode(body.length);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body);
                    }
                }
                conn.connect();
                int status = conn.getResponseCode();
                String returnedContentType = conn.getHeaderField("Content-Type");
                String transferEncoding = conn.getHeaderField("Transfer-Encoding");
                InputStream in = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
                byte[] respBody = in != null ? readAll(in) : new byte[0];
                conn.disconnect();
                return new HttpClientResult(status, returnedContentType, transferEncoding, respBody);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int n;
        while ((n = in.read(chunk)) > 0) {
            buf.write(chunk, 0, n);
        }
        return buf.toByteArray();
    }

    private static byte[] repeat(String fragment, int times) {
        StringBuilder sb = new StringBuilder(fragment.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(fragment);
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Snapshot of the response observable by the HTTP client. */
    private static final class HttpClientResult {
        final int statusCode;
        final String contentType;
        final String transferEncoding;
        final byte[] body;

        HttpClientResult(int statusCode, String contentType, String transferEncoding, byte[] body) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.transferEncoding = transferEncoding;
            this.body = body;
        }
    }
}
