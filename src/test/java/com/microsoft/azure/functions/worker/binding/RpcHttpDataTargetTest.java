package com.microsoft.azure.functions.worker.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.microsoft.azure.functions.HttpResponseMessage.IOConsumer;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.rpc.messages.RpcHttp;
import com.microsoft.azure.functions.rpc.messages.TypedData;

import org.junit.jupiter.api.Test;

public class RpcHttpDataTargetTest {

    @Test
    public void isStreamingBodyDetectsInputStream() {
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        assertTrue(RpcHttpDataTarget.isStreamingBody(stream));
    }

    @Test
    public void isStreamingBodyDetectsIOConsumer() {
        IOConsumer<java.io.OutputStream> writer = out -> out.write(1);
        assertTrue(RpcHttpDataTarget.isStreamingBody(writer));
    }

    @Test
    public void isStreamingBodyRejectsNull() {
        assertFalse(RpcHttpDataTarget.isStreamingBody(null));
    }

    @Test
    public void isStreamingBodyRejectsString() {
        assertFalse(RpcHttpDataTarget.isStreamingBody("hello"));
    }

    @Test
    public void isStreamingBodyRejectsByteArray() {
        assertFalse(RpcHttpDataTarget.isStreamingBody(new byte[]{1, 2, 3}));
    }

    @Test
    public void toRpcHttpDataSkipsBodyForInputStream() throws Exception {
        RpcHttpDataTarget target = new RpcHttpDataTarget();
        target.status(HttpStatus.OK)
              .header("Content-Type", "text/event-stream")
              .body(new ByteArrayInputStream("ignored".getBytes(StandardCharsets.UTF_8)));

        TypedData.Builder builder = RpcHttpDataTarget.toRpcHttpData(target);
        RpcHttp http = builder.getHttp();

        assertEquals("200", http.getStatusCode());
        assertEquals("text/event-stream", http.getHeadersOrDefault("Content-Type", null));
        // Streaming bodies are NOT serialized into the protobuf envelope; the field is unset.
        assertFalse(http.hasBody(), "InputStream body should not be serialized into RpcHttp.body");
    }

    @Test
    public void toRpcHttpDataSkipsBodyForIOConsumer() throws Exception {
        RpcHttpDataTarget target = new RpcHttpDataTarget();
        target.status(HttpStatus.ACCEPTED)
              .header("X-Trace", "abc")
              .body((IOConsumer<java.io.OutputStream>) (out -> out.write(0)));

        TypedData.Builder builder = RpcHttpDataTarget.toRpcHttpData(target);
        RpcHttp http = builder.getHttp();

        assertEquals("202", http.getStatusCode());
        assertEquals("abc", http.getHeadersOrDefault("X-Trace", null));
        assertFalse(http.hasBody(), "IOConsumer body should not be serialized into RpcHttp.body");
    }

    @Test
    public void toRpcHttpDataSerializesStringBody() throws Exception {
        RpcHttpDataTarget target = new RpcHttpDataTarget();
        target.status(HttpStatus.OK)
              .body("hello");

        TypedData.Builder builder = RpcHttpDataTarget.toRpcHttpData(target);
        RpcHttp http = builder.getHttp();

        assertEquals("200", http.getStatusCode());
        assertTrue(http.hasBody(), "Non-streaming body should be serialized");
        assertEquals("hello", http.getBody().getString());
    }
}
