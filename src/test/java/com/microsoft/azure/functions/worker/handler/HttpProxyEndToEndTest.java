package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.annotation.HttpOutput;
import com.microsoft.azure.functions.annotation.QueueOutput;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.rpc.messages.BindingInfo;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.InvocationResponse;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker.HttpInvocationOutcome;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;
import com.microsoft.azure.functions.worker.http.HttpInvocationCoordinator;
import com.microsoft.azure.functions.worker.http.HttpProxyHandler;
import com.microsoft.azure.functions.worker.http.HttpProxyServer;
import com.microsoft.azure.functions.worker.http.ProxyConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Real-socket integration of HTTP handoffs and simulated gRPC invocations.
 * Mocked brokers isolate transport behavior; real brokers exercise request
 * binding, user function execution and response serialization together.
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
        server = new HttpProxyServer(ProxyConfig.defaults(), coordinator);
        proxyUri = server.start();
        brokerMock = mock(JavaFunctionBroker.class);
        when(brokerMock.getMethodName(anyString())).thenReturn(Optional.of("TestFn"));
        when(brokerMock.isHttpTrigger(anyString())).thenReturn(true);
        handler = new InvocationRequestHandler(brokerMock, server);
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

    @Test
    public void bufferedRequest_bufferedResponse_roundTrips() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        AtomicReference<InvocationRequest> seenRequest = new AtomicReference<>();
        AtomicReference<InputStream> seenBody = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    seenRequest.set(inv.getArgument(1));
                    seenBody.set(inv.getArgument(2));
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
        assertEquals("text/plain; charset=utf-8", resp.contentType);

        // The buffered path must have folded the HTTP body into the RpcHttp envelope.
        // For text/* content types the bridge stores the body as a string, not bytes
        // (mirrors host PopulateBody behavior).
        TypedData httpInput = seenRequest.get().getInputDataList().get(0).getData();
        assertEquals("ping", httpInput.getHttp().getBody().getString());
        assertNull(seenBody.get(), "Buffered-input invocations must not receive a streaming request body");
    }

    @Test
    public void bufferedRequest_streamingInputStreamResponse_streamsBackUnbuffered() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        byte[] payload = repeat("stream-chunk-", 5000); // ~65KB to force multi-chunk write
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
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

    @Test
    public void bufferedRequest_streamingIOConsumerResponse_streamsBack() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);

        IOConsumer<OutputStream> writer = out -> {
            for (int i = 0; i < 100; i++) {
                out.write(("event: tick\ndata: " + i + "\n\n").getBytes(StandardCharsets.UTF_8));
            }
        };
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
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

    @Test
    public void streamingRequest_bufferedResponse_skipsBodyReadAndExposesLiveStream() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(true);

        byte[] uploadPayload = repeat("upload-frag-", 4096); // ~50KB
        AtomicReference<byte[]> consumedByBroker = new AtomicReference<>();
        AtomicReference<InvocationRequest> seenRequest = new AtomicReference<>();
        AtomicReference<InputStream> seenBody = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    seenRequest.set(inv.getArgument(1));
                    InputStream body = inv.getArgument(2);
                    seenBody.set(body);
                    // Consume the live request body to validate the streaming path actually wired through.
                    if (body != null) {
                        consumedByBroker.set(readAll(body));
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
        assertNotNull(seenBody.get(), "Streaming-input invocation must receive the live request body");
        // The bytes the broker read off the live exchange must equal what the client sent.
        assertNotNull(consumedByBroker.get());
        assertTrue(java.util.Arrays.equals(uploadPayload, consumedByBroker.get()),
                "Bytes read from the live exchange must equal the bytes the client sent");
    }

    @Test
    public void streamingRequest_streamingResponse_endToEnd() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(true);

        byte[] uploadPayload = repeat("full-stream-", 1024);
        AtomicReference<byte[]> echoedBack = new AtomicReference<>();
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
                .thenAnswer((InvocationOnMock inv) -> {
                    byte[] consumed = readAll(inv.<InputStream>getArgument(2));
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

    @Test
    public void coordinatorSlotReleasedAfterSuccessfulInvocation() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
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

    @Test
    public void brokerExceptionPropagatesAsHttp500() throws Exception {
        when(brokerMock.methodHasStreamingHttpBody(anyString())).thenReturn(false);
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("user function blew up"));

        CompletableFuture<HttpClientResult> futureResp = sendHttpRequestAsync(
                "GET", "/api/boom", null, new byte[0]);

        awaitHttpArrival();
        assertThrows(RuntimeException.class, this::runGrpcArrival);

        HttpClientResult resp = futureResp.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);
        assertEquals(500, resp.statusCode);
        assertEquals("Invocation failed", new String(resp.body, StandardCharsets.UTF_8),
                "500 body must not leak the underlying failure message");
    }

    @Test
    public void legacyHostDoesNotWaitForAnHttpHandoff() throws Exception {
        InvocationRequest legacyRequest = buildInvocationRequest().toBuilder().setInputData(0,
            ParameterBinding.newBuilder().setName("req").setData(TypedData.newBuilder().setHttp(
                RpcHttp.newBuilder().setMethod("POST").setUrl("https://example.test/api/echo")
                    .setBody(TypedData.newBuilder().setString("legacy"))))).build();
        handler.execute(legacyRequest, InvocationResponse.newBuilder());
        verify(brokerMock).invokeMethod(anyString(), any(), any());
        verify(brokerMock, never()).invokeMethodForHttpProxy(anyString(), any(), any(), any());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void stoppedProxyUsesGrpcPath() throws Exception {
        server.close();
        handler.execute(buildInvocationRequest(), InvocationResponse.newBuilder());
        verify(brokerMock).invokeMethod(anyString(), any(), any());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void disabledProxyUsesGrpcPath() throws Exception {
        new InvocationRequestHandler(brokerMock).execute(buildInvocationRequest(), InvocationResponse.newBuilder());
        verify(brokerMock).invokeMethod(anyString(), any(), any());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void httpTypedInputOnNonHttpTriggerDoesNotWaitForProxy() throws Exception {
        when(brokerMock.isHttpTrigger(FUNCTION_ID)).thenReturn(false);
        handler.execute(buildInvocationRequest(), InvocationResponse.newBuilder());
        verify(brokerMock).invokeMethod(anyString(), any(), any());
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void realBrokerStreamsAssignedReturnBody() throws Exception {
        JavaFunctionBroker broker = loadFunction("echo");
        handler = new InvocationRequestHandler(broker, server);
        byte[] payload = repeat("real-broker-", 4096);
        CompletableFuture<HttpClientResult> response = sendHttpRequestAsync(
            "POST", "/api/real", "application/octet-stream", payload);
        awaitHttpArrival();
        InvocationResponse.Builder grpcResponse = InvocationResponse.newBuilder();
        handler.execute(buildInvocationRequest(), grpcResponse);

        HttpClientResult http = response.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);
        assertEquals(200, http.statusCode);
        org.junit.jupiter.api.Assertions.assertArrayEquals(payload, http.body);
        assertFalse(grpcResponse.hasReturnValue(), "HTTP output must travel only over HTTP");
        assertEquals(0, coordinator.activeInvocationCount());
    }

    @Test
    public void realBrokerPreservesNonHttpOutputBindings() throws Exception {
        handler = new InvocationRequestHandler(loadFunction("sideOutput"), server);
        CompletableFuture<HttpClientResult> response = sendHttpRequestAsync(
            "POST", "/api/side-output", "text/plain", new byte[0]);
        awaitHttpArrival();
        InvocationResponse.Builder grpcResponse = InvocationResponse.newBuilder();
        handler.execute(buildInvocationRequest(), grpcResponse);

        assertEquals("ok", new String(response.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS).body, StandardCharsets.UTF_8));
        assertFalse(grpcResponse.hasReturnValue());
        assertEquals(1, grpcResponse.getOutputDataCount());
        assertEquals("queue", grpcResponse.getOutputData(0).getName());
        assertEquals("side effect", grpcResponse.getOutputData(0).getData().getString());
    }

    @Test
    public void realBrokerStreamsNamedHttpOutput() throws Exception {
        handler = new InvocationRequestHandler(loadFunction("namedOutput"), server);
        byte[] payload = repeat("named-output-", 4096);
        CompletableFuture<HttpClientResult> response = sendHttpRequestAsync(
            "POST", "/api/named-output", "application/octet-stream", payload);
        awaitHttpArrival();
        InvocationResponse.Builder grpcResponse = InvocationResponse.newBuilder();
        handler.execute(buildInvocationRequest(), grpcResponse);

        org.junit.jupiter.api.Assertions.assertArrayEquals(payload,
            response.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS).body);
        assertEquals(0, grpcResponse.getOutputDataCount());
    }

    @Test
    public void realBrokerClosesAssignedResponseWhenFunctionThrows() throws Exception {
        try {
            for (boolean proxied : new boolean[]{true, false}) {
                InputStream source = mock(InputStream.class);
                Functions.failedResponseBody = source;
                JavaFunctionBroker broker = loadFunction("namedFailure");
                assertThrows(Exception.class, () -> {
                    if (proxied) {
                        broker.invokeMethodForHttpProxy(FUNCTION_ID, buildInvocationRequest(), null, new ArrayList<>());
                    } else {
                        broker.invokeMethod(FUNCTION_ID, buildInvocationRequest(), new ArrayList<>());
                    }
                });
                verify(source).close();
            }
        } finally {
            Functions.failedResponseBody = null;
        }
    }

    @Test
    public void realBrokerPreservesPlainStringHttpReturn() throws Exception {
        handler = new InvocationRequestHandler(loadFunction("plain"), server);
        CompletableFuture<HttpClientResult> response = sendHttpRequestAsync(
            "GET", "/api/plain", null, new byte[0]);
        awaitHttpArrival();
        runGrpcArrival();
        HttpClientResult http = response.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS);
        assertEquals("plain", new String(http.body, StandardCharsets.UTF_8));
        assertEquals("text/plain; charset=utf-8", http.contentType);
    }

    @Test
    public void realBrokerRejectsStreamingResponseWithoutProxy() throws Exception {
        JavaFunctionBroker broker = loadFunction("download");
        InvocationRequest legacyRequest = buildInvocationRequest().toBuilder().setInputData(0,
            ParameterBinding.newBuilder().setName("req").setData(TypedData.newBuilder().setHttp(
                RpcHttp.newBuilder().setMethod("GET").setUrl("https://example.test/api/download")
                    .setBody(TypedData.newBuilder().setString("request"))))).build();

        UnsupportedOperationException error = assertThrows(UnsupportedOperationException.class,
            () -> new InvocationRequestHandler(broker).execute(legacyRequest, InvocationResponse.newBuilder()));
        assertTrue(error.getMessage().contains("HTTP proxy"));
    }

    @Test
    public void firstResponseBytesArriveBeforeInputStreamCompletes() throws Exception {
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        byte[] first = "first\n".getBytes(StandardCharsets.UTF_8);
        InputStream source = new ByteArrayInputStream(first) {
            private boolean waiting;

            @Override
            public synchronized int read(byte[] bytes, int offset, int length) {
                int result = super.read(bytes, offset, length);
                if (result == -1 && !waiting) {
                    waiting = true;
                    try {
                        assertTrue(firstChunkReceived.await(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS),
                            "First chunk must be visible while the source is still open");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(ex);
                    }
                }
                return result;
            }
        };
        when(brokerMock.invokeMethodForHttpProxy(anyString(), any(), any(), any()))
            .thenReturn(new HttpInvocationOutcome(Optional.of(TypedData.newBuilder()
                .setHttp(RpcHttp.newBuilder().setStatusCode("200")).build()), source));
        CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection connection = openConnection("GET", "/api/progressive");
                try (InputStream body = connection.getInputStream()) {
                    byte[] bytes = new byte[first.length];
                    for (int offset = 0; offset < bytes.length;) {
                        int count = body.read(bytes, offset, bytes.length - offset);
                        if (count == -1) {
                            throw new IOException("Response ended before first chunk");
                        }
                        offset += count;
                    }
                    firstChunkReceived.countDown();
                    assertEquals(-1, body.read());
                    return bytes;
                } finally {
                    connection.disconnect();
                }
            } catch (IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
        awaitHttpArrival();
        runGrpcArrival();
        org.junit.jupiter.api.Assertions.assertArrayEquals(first,
            received.get(HTTP_AWAIT_MS, TimeUnit.MILLISECONDS));
    }

    private JavaFunctionBroker loadFunction(String method) throws Exception {
        ClassLoaderProvider classLoaders = mock(ClassLoaderProvider.class);
        when(classLoaders.createClassLoader()).thenReturn(getClass().getClassLoader());
        JavaFunctionBroker broker = new JavaFunctionBroker(classLoaders);
        Map<String, BindingInfo> bindings = new HashMap<>();
        bindings.put("req", BindingInfo.newBuilder().setType("httpTrigger")
            .setDirection(BindingInfo.Direction.in).build());
        bindings.put("$return", BindingInfo.newBuilder().setType("http")
            .setDirection(BindingInfo.Direction.out).build());
        bindings.put("res", BindingInfo.newBuilder().setType("http")
            .setDirection(BindingInfo.Direction.out).build());
        bindings.put("queue", BindingInfo.newBuilder().setType("queue")
            .setDirection(BindingInfo.Direction.out).build());
        broker.loadMethod(new FunctionMethodDescriptor(FUNCTION_ID, "TestFn",
            Functions.class.getName() + "." + method,
            new java.io.File(System.getProperty("testing-project-jar")).getAbsolutePath(), true), bindings);
        return broker;
    }

    public static class Functions {
        static InputStream failedResponseBody;

        public HttpResponseMessage echo(@HttpTrigger(name = "req") HttpRequestMessage<InputStream> request) {
            assertEquals("POST", request.getHttpMethod().name());
            assertEquals("/api/real", request.getUri().getPath());
            return request.createResponseBuilder(HttpStatus.OK).bodyStream(request.getBody()).build();
        }

        public HttpResponseMessage download(@HttpTrigger(name = "req") HttpRequestMessage<String> request) {
            return request.createResponseBuilder(HttpStatus.OK)
                .bodyStream(new ByteArrayInputStream(new byte[]{1, 2, 3})).build();
        }

        public HttpResponseMessage sideOutput(@HttpTrigger(name = "req") HttpRequestMessage<InputStream> request,
                @QueueOutput(name = "queue", queueName = "unused") OutputBinding<String> queue) {
            queue.setValue("side effect");
            return request.createResponseBuilder(HttpStatus.OK).body("ok").build();
        }

        public void namedOutput(@HttpTrigger(name = "req") HttpRequestMessage<InputStream> request,
                @HttpOutput(name = "res") OutputBinding<HttpResponseMessage> response) {
            response.setValue(request.createResponseBuilder(HttpStatus.OK).bodyStream(request.getBody()).build());
        }

        public void namedFailure(@HttpTrigger(name = "req") HttpRequestMessage<String> request,
                @HttpOutput(name = "res") OutputBinding<HttpResponseMessage> response) {
            response.setValue(request.createResponseBuilder(HttpStatus.OK).bodyStream(failedResponseBody).build());
            throw new IllegalStateException("function failed after assigning its response");
        }

        public String plain(@HttpTrigger(name = "req") HttpRequestMessage<Optional<String>> request) {
            return "plain";
        }
    }


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
        RpcHttp httpEnvelope = RpcHttp.getDefaultInstance();
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
                HttpURLConnection conn = openConnection(method, path);
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

    private HttpURLConnection openConnection(String method, String path) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(proxyUri + path).toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setRequestProperty(HttpProxyHandler.INVOCATION_ID_HEADER, INVOCATION_ID);
        connection.setConnectTimeout((int) HTTP_AWAIT_MS);
        connection.setReadTimeout((int) HTTP_AWAIT_MS);
        return connection;
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
