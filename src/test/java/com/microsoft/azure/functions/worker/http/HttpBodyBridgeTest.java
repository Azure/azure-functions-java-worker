package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.Test;

public class HttpBodyBridgeTest {

    @Test
    public void buildBodyTypedDataJsonContentType() {
        TypedData data = HttpBodyBridge.buildBodyTypedData(
            "{\"k\":1}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
        assertEquals("{\"k\":1}", data.getString());
    }

    @Test
    public void buildBodyTypedDataJsonWithCharsetSuffix() {
        TypedData data = HttpBodyBridge.buildBodyTypedData(
            "{}".getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
    }

    @Test
    public void buildBodyTypedDataTextContentType() {
        TypedData data = HttpBodyBridge.buildBodyTypedData(
            "hello".getBytes(StandardCharsets.UTF_8), "text/plain");
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
        assertEquals("hello", data.getString());
    }

    @Test
    public void buildBodyTypedDataFormEncoded() {
        TypedData data = HttpBodyBridge.buildBodyTypedData(
            "a=1&b=2".getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
    }

    @Test
    public void buildBodyTypedDataTextWhenNoContentType() {
        byte[] bytes = new byte[]{1, 2, 3};
        TypedData data = HttpBodyBridge.buildBodyTypedData(bytes, null);
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
        assertEquals(new String(bytes, StandardCharsets.UTF_8), data.getString());
    }

    @Test
    public void buildBodyTypedDataBinaryWhenOctetStream() {
        byte[] bytes = "binary".getBytes(StandardCharsets.UTF_8);
        TypedData data = HttpBodyBridge.buildBodyTypedData(bytes, "application/octet-stream");
        assertEquals(TypedData.DataCase.BYTES, data.getDataCase());
    }

    @Test
    public void buildBodyTypedDataRespectsCharset() {
        byte[] bytes = "héllo".getBytes(StandardCharsets.ISO_8859_1);
        TypedData data = HttpBodyBridge.buildBodyTypedData(bytes, "text/plain; charset=ISO-8859-1");
        assertEquals(TypedData.DataCase.STRING, data.getDataCase());
        assertEquals("héllo", data.getString());
    }

    @Test
    public void emptyBodyRemainsUnsetAndMultipartRemainsBinary() {
        assertEquals(TypedData.DataCase.DATA_NOT_SET,
            HttpBodyBridge.buildBodyTypedData(new byte[0], "text/plain").getDataCase());
        byte[] body = new byte[]{0, (byte) 255};
        assertArrayEquals(body, HttpBodyBridge.buildBodyTypedData(body,
            "multipart/form-data; boundary=x").getBytes().toByteArray());
    }

    @Test
    public void enrichesNativeMetadataWithoutConsumingStreamingBody() throws Exception {
        InvocationRequest request = InvocationRequest.newBuilder().addInputData(
            ParameterBinding.newBuilder().setName("req").setData(TypedData.newBuilder().setHttp(
                RpcHttp.newBuilder().putParams("id", "host-resolved")))).build();
        HttpExchange exchange = mockExchangeWithBody(new byte[]{1}, "application/octet-stream");
        java.io.InputStream body = mock(java.io.InputStream.class);
        when(exchange.getRequestBody()).thenReturn(body);
        when(exchange.getRequestMethod()).thenReturn("PATCH");
        when(exchange.getRequestURI()).thenReturn(java.net.URI.create(
            "/api/items/a%2Fb?q=a+b&q=c%2Bd&empty=&encoded=%E2%82%AC"));
        exchange.getRequestHeaders().set("X-Forwarded-Proto", "https");
        exchange.getRequestHeaders().set("X-Forwarded-Host", "example.test:8443");
        exchange.getRequestHeaders().add("X-Tag", "one");
        exchange.getRequestHeaders().add("X-Tag", "two");

        RpcHttp http = HttpBodyBridge.enrichRequest(request, exchange, false).getInputData(0).getData().getHttp();

        assertEquals("PATCH", http.getMethod());
        assertEquals("https://example.test:8443/api/items/a%2Fb?q=a+b&q=c%2Bd&empty=&encoded=%E2%82%AC",
            http.getUrl());
        assertEquals("host-resolved", http.getParamsOrThrow("id"));
        assertEquals("example.test:8443", http.getHeadersOrThrow("host"));
        assertEquals("one,two", http.getHeadersOrThrow("x-tag"));
        assertEquals("a b,c+d", http.getQueryOrThrow("q"));
        assertEquals("\u20ac", http.getQueryOrThrow("encoded"));
        org.junit.jupiter.api.Assertions.assertFalse(http.containsQuery("empty"));
        assertEquals(TypedData.DataCase.DATA_NOT_SET, http.getBody().getDataCase());
        verifyNoInteractions(body);
    }

    @Test
    public void enrichRequestWithBodyReplacesHttpBody() throws Exception {
        InvocationRequest original = InvocationRequest.newBuilder()
            .setInvocationId("inv-1")
            .addInputData(ParameterBinding.newBuilder()
                .setName("req")
                .setData(TypedData.newBuilder()
                    .setHttp(RpcHttp.newBuilder().setMethod("POST").setUrl("http://localhost/api/x"))))
            .build();
        HttpExchange exchange = mockExchangeWithBody("payload".getBytes(StandardCharsets.UTF_8), "text/plain");

        InvocationRequest enriched = HttpBodyBridge.enrichRequest(original, exchange, true);
        assertNotSame(original, enriched);
        TypedData body = enriched.getInputData(0).getData().getHttp().getBody();
        assertEquals(TypedData.DataCase.STRING, body.getDataCase());
        assertEquals("payload", body.getString());
        // Method/url preserved.
        assertEquals("POST", enriched.getInputData(0).getData().getHttp().getMethod());
    }

    @Test
    public void enrichRequestWithBodyReturnsSameWhenNoHttpInput() throws Exception {
        InvocationRequest original = InvocationRequest.newBuilder()
            .setInvocationId("inv-1")
            .addInputData(ParameterBinding.newBuilder()
                .setName("queueItem")
                .setData(TypedData.newBuilder().setString("hello")))
            .build();
        HttpExchange exchange = mock(HttpExchange.class);
        InvocationRequest result = HttpBodyBridge.enrichRequest(original, exchange, true);
        assertSame(original, result, "Non-HTTP requests should not be modified");
    }

    @Test
    public void enrichRequestReadsChunkedBodyLargerThanBuffer() throws Exception {
        // Simulate transfer-encoding: chunked by providing a body larger than the read buffer.
        byte[] big = new byte[20_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i & 0xff);
        }
        InvocationRequest original = InvocationRequest.newBuilder()
            .setInvocationId("inv-big")
            .addInputData(ParameterBinding.newBuilder().setName("req")
                .setData(TypedData.newBuilder().setHttp(RpcHttp.newBuilder().setMethod("POST"))))
            .build();
        HttpExchange exchange = mockExchangeWithBody(big, "application/octet-stream");

        InvocationRequest enriched = HttpBodyBridge.enrichRequest(original, exchange, true);
        TypedData body = enriched.getInputData(0).getData().getHttp().getBody();
        assertEquals(TypedData.DataCase.BYTES, body.getDataCase());
        assertArrayEquals(big, body.getBytes().toByteArray());
    }

    @Test
    public void writeRpcHttpResponseWritesStatusHeadersAndBody() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Headers responseHeaders = new Headers();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp response = RpcHttp.newBuilder()
            .setStatusCode("201")
            .putHeaders("Content-Type", "application/json")
            .putHeaders("X-Custom", "value")
            .setBody(TypedData.newBuilder().setJson("{\"ok\":true}"))
            .build();

        HttpBodyBridge.writeRpcHttpResponse(exchange, response);

        byte[] expected = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
        verify(exchange).sendResponseHeaders(201, expected.length);
        assertArrayEquals(expected, captured.toByteArray());
        assertEquals("application/json; charset=utf-8", responseHeaders.getFirst("Content-Type"));
        assertEquals("value", responseHeaders.getFirst("X-Custom"));
    }

    @Test
    public void writeRpcHttpResponseHandlesEmptyBody() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp response = RpcHttp.newBuilder().setStatusCode("204").build();
        HttpBodyBridge.writeRpcHttpResponse(exchange, response);

        verify(exchange).sendResponseHeaders(204, -1);
        assertEquals(0, captured.size());
    }

    @Test
    public void writeRpcHttpResponseHandlesBytesBody() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        byte[] payload = new byte[]{0x01, 0x02, 0x03};
        RpcHttp response = RpcHttp.newBuilder()
            .setStatusCode("200")
            .setBody(TypedData.newBuilder().setBytes(ByteString.copyFrom(payload)))
            .build();
        HttpBodyBridge.writeRpcHttpResponse(exchange, response);

        verify(exchange).sendResponseHeaders(200, payload.length);
        assertArrayEquals(payload, captured.toByteArray());
    }

    @Test
    public void writeRpcHttpResponseRejectsInvalidStatus() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp response = RpcHttp.newBuilder().setStatusCode("not-a-number").build();
        org.junit.jupiter.api.Assertions.assertThrows(NumberFormatException.class,
            () -> HttpBodyBridge.writeRpcHttpResponse(exchange, response));
    }

    @Test
    public void writeErrorResponseWritesPlainText() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Headers headers = new Headers();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(headers);
        when(exchange.getResponseBody()).thenReturn(captured);

        HttpBodyBridge.writeErrorResponse(exchange, 418, "I'm a teapot");

        byte[] expected = "I'm a teapot".getBytes(StandardCharsets.UTF_8);
        verify(exchange).sendResponseHeaders(418, expected.length);
        assertArrayEquals(expected, captured.toByteArray());
        assertTrue(headers.getFirst("Content-Type").startsWith("text/plain"));
    }

    @Test
    public void writeStreamingResponseFromInputStreamUsesChunkedEncoding() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Headers responseHeaders = new Headers();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp envelope = RpcHttp.newBuilder()
            .setStatusCode("200")
            .putHeaders("Content-Type", "text/event-stream")
            .putHeaders("Cache-Control", "no-cache")
            .build();
        byte[] payload = "data: one\n\ndata: two\n\n".getBytes(StandardCharsets.UTF_8);
        HttpBodyBridge.writeStreamingResponse(exchange, envelope, new ByteArrayInputStream(payload));

        // length=0 selects chunked transfer-encoding (or close-delimited for HTTP/1.0).
        verify(exchange).sendResponseHeaders(200, 0);
        assertArrayEquals(payload, captured.toByteArray());
        assertEquals("text/event-stream", responseHeaders.getFirst("Content-Type"));
        assertEquals("no-cache", responseHeaders.getFirst("Cache-Control"));
    }

    @Test
    public void writeStreamingResponseFromInputStreamHandlesLargePayload() throws Exception {
        // Larger than the 8KB read chunk; verifies the copy loop iterates correctly.
        byte[] big = new byte[32_768];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (i & 0xff);
        }
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp envelope = RpcHttp.newBuilder().setStatusCode("200").build();
        HttpBodyBridge.writeStreamingResponse(exchange, envelope, new ByteArrayInputStream(big));

        verify(exchange).sendResponseHeaders(200, 0);
        assertArrayEquals(big, captured.toByteArray());
    }

    @Test
    public void writeStreamingResponseFromInputStreamClosesSource() throws Exception {
        boolean[] closed = new boolean[]{false};
        ByteArrayInputStream backing = new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8));
        java.io.InputStream tracking = new java.io.FilterInputStream(backing) {
            @Override
            public void close() throws IOException {
                closed[0] = true;
                super.close();
            }
        };
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());

        HttpBodyBridge.writeStreamingResponse(
            exchange, RpcHttp.newBuilder().setStatusCode("200").build(), tracking);

        assertTrue(closed[0], "Streaming InputStream body should be closed");
    }

    @Test
    public void writeStreamingResponseClosesSourceWhenHeadersFail() throws Exception {
        java.io.InputStream body = mock(java.io.InputStream.class);
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        doThrow(new IOException("disconnected")).when(exchange).sendResponseHeaders(200, 0);

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
            HttpBodyBridge.writeStreamingResponse(exchange, RpcHttp.getDefaultInstance(), body));

        verify(body).close();
    }

    @Test
    public void writeStreamingResponseFromIOConsumerInvokesWriterAndFlushes() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Headers responseHeaders = new Headers();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp envelope = RpcHttp.newBuilder()
            .setStatusCode("202")
            .putHeaders("X-Trace", "abc")
            .build();
        HttpBodyBridge.writeStreamingResponse(exchange, envelope, out -> {
            out.write("chunk-1\n".getBytes(StandardCharsets.UTF_8));
            out.write("chunk-2\n".getBytes(StandardCharsets.UTF_8));
        });

        verify(exchange).sendResponseHeaders(202, 0);
        assertEquals("chunk-1\nchunk-2\n", captured.toString("UTF-8"));
        assertEquals("abc", responseHeaders.getFirst("X-Trace"));
    }

    @Test
    public void writeStreamingResponseFromIOConsumerPropagatesIOException() {
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(new ByteArrayOutputStream());

        IOException expected = new IOException("writer-failed");
        IOException thrown = org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () ->
            HttpBodyBridge.writeStreamingResponse(
                exchange,
                RpcHttp.newBuilder().setStatusCode("200").build(),
                out -> { throw expected; }));
        assertSame(expected, thrown);
    }

    @Test
    public void bodylessStatusesAndHeadNeverReadSource() throws Exception {
        for (int status : new int[]{200, 204, 205, 304}) {
            HttpExchange exchange = mock(HttpExchange.class);
            when(exchange.getResponseHeaders()).thenReturn(new Headers());
            when(exchange.getRequestMethod()).thenReturn(status == 200 ? "HEAD" : "GET");
            java.io.InputStream source = mock(java.io.InputStream.class);
            HttpBodyBridge.writeStreamingResponse(exchange,
                RpcHttp.newBuilder().setStatusCode(Integer.toString(status)).build(), source);
            verify(exchange).sendResponseHeaders(status, -1);
            verify(exchange, never()).getResponseBody();
            verify(source, never()).read(org.mockito.ArgumentMatchers.any(byte[].class));
            verify(source).close();
        }
    }

    @Test
    public void headDoesNotInvokeWriterAndIgnoresConflictingFramingHeaders() throws Exception {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        when(exchange.getResponseHeaders()).thenReturn(headers);
        when(exchange.getRequestMethod()).thenReturn("HEAD");
        HttpBodyBridge.writeStreamingResponse(exchange, RpcHttp.newBuilder().setStatusCode("200")
            .putHeaders("content-length", "999").putHeaders("transfer-encoding", "identity").build(),
            out -> { throw new AssertionError("HEAD must not invoke a streaming writer"); });
        verify(exchange).sendResponseHeaders(200, -1);
        org.junit.jupiter.api.Assertions.assertNull(headers.getFirst("Content-Length"));
        org.junit.jupiter.api.Assertions.assertNull(headers.getFirst("Transfer-Encoding"));
    }

    @Test
    public void numericResponseBodiesUseWireValuesNotProtobufDebugText() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);
        HttpBodyBridge.writeRpcHttpResponse(exchange, RpcHttp.newBuilder()
            .setBody(TypedData.newBuilder().setInt(42)).build());
        assertEquals("42", captured.toString("UTF-8"));
    }

    private static HttpExchange mockExchangeWithBody(byte[] body, String contentType) throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        if (contentType != null) {
            headers.add("Content-Type", contentType);
        }
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body));
        headers.set("Host", "localhost");
        when(exchange.getRequestMethod()).thenReturn("POST");
        when(exchange.getRequestURI()).thenReturn(java.net.URI.create("/api/x"));
        return exchange;
    }
}
