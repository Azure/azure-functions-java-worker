package com.microsoft.azure.functions.worker.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertEquals(TypedData.DataCase.JSON, data.getDataCase());
        assertEquals("{\"k\":1}", data.getJson());
    }

    @Test
    public void buildBodyTypedDataJsonWithCharsetSuffix() {
        TypedData data = HttpBodyBridge.buildBodyTypedData(
            "{}".getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
        assertEquals(TypedData.DataCase.JSON, data.getDataCase());
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
    public void buildBodyTypedDataBinaryWhenNoContentType() {
        byte[] bytes = new byte[]{1, 2, 3};
        TypedData data = HttpBodyBridge.buildBodyTypedData(bytes, null);
        assertEquals(TypedData.DataCase.BYTES, data.getDataCase());
        assertArrayEquals(bytes, data.getBytes().toByteArray());
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
    public void enrichRequestWithBodyReplacesHttpBody() throws Exception {
        InvocationRequest original = InvocationRequest.newBuilder()
            .setInvocationId("inv-1")
            .addInputData(ParameterBinding.newBuilder()
                .setName("req")
                .setData(TypedData.newBuilder()
                    .setHttp(RpcHttp.newBuilder().setMethod("POST").setUrl("http://localhost/api/x"))))
            .build();
        HttpExchange exchange = mockExchangeWithBody("payload".getBytes(StandardCharsets.UTF_8), "text/plain");

        InvocationRequest enriched = HttpBodyBridge.enrichRequestWithBody(original, exchange);
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
        InvocationRequest result = HttpBodyBridge.enrichRequestWithBody(original, exchange);
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

        InvocationRequest enriched = HttpBodyBridge.enrichRequestWithBody(original, exchange);
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
        assertEquals("application/json", responseHeaders.getFirst("Content-Type"));
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
    public void writeRpcHttpResponseDefaultsInvalidStatusTo500() throws Exception {
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        HttpExchange exchange = mock(HttpExchange.class);
        when(exchange.getResponseHeaders()).thenReturn(new Headers());
        when(exchange.getResponseBody()).thenReturn(captured);

        RpcHttp response = RpcHttp.newBuilder().setStatusCode("not-a-number").build();
        HttpBodyBridge.writeRpcHttpResponse(exchange, response);

        verify(exchange).sendResponseHeaders(500, -1);
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

    private static HttpExchange mockExchangeWithBody(byte[] body, String contentType) throws IOException {
        HttpExchange exchange = mock(HttpExchange.class);
        Headers headers = new Headers();
        if (contentType != null) {
            headers.add("Content-Type", contentType);
        }
        when(exchange.getRequestHeaders()).thenReturn(headers);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(body));
        return exchange;
    }
}
