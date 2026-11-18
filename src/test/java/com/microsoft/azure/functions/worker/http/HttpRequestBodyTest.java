package com.microsoft.azure.functions.worker.http;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class HttpRequestBodyTest {
    @Test
    public void functionClosingItsStreamDoesNotPreventTransportDrain() throws Exception {
        ByteArrayInputStream source = new ByteArrayInputStream(new byte[]{1, 2, 3});
        try (HttpRequestBody body = new HttpRequestBody(source)) {
            InputStream userStream = body.getStream();
            assertEquals(1, userStream.read());
            userStream.close();
            body.prepareForResponse();
            assertEquals(0, source.available(), "The upload must finish before the response starts");
            assertThrows(IOException.class, userStream::read);
        }
    }

    @Test
    public void stagesOnlyUnreadBytesAndDeletesTheFileOnClose() throws Exception {
        Path staged;
        try (HttpRequestBody body = new HttpRequestBody(new ByteArrayInputStream(new byte[]{1, 2, 3}))) {
            assertEquals(1, body.read());
            body.prepareForResponse();
            staged = stagedPath(body);
            assertNotNull(staged);
            assertArrayEquals(new byte[]{2, 3}, readAll(body));
        }
        assertFalse(Files.exists(staged));
    }

    @Test
    public void fullyReadBodyDoesNotCreateATemporaryFile() throws Exception {
        try (HttpRequestBody body = new HttpRequestBody(new ByteArrayInputStream(new byte[]{1}))) {
            assertEquals(1, body.read());
            assertEquals(-1, body.read());
            body.prepareForResponse();
            assertNull(stagedPath(body));
        }
    }

    @Test
    public void preparationIsIdempotent() throws Exception {
        try (HttpRequestBody body = new HttpRequestBody(new ByteArrayInputStream(new byte[]{1, 2}))) {
            body.prepareForResponse();
            assertEquals(1, body.read());
            body.prepareForResponse();
            assertEquals(2, body.read());
            assertEquals(-1, body.read());
        }
    }

    @Test
    public void failedStagingClosesSourceAndRemovesPartialFile() throws Exception {
        InputStream source = mock(InputStream.class);
        when(source.read()).thenReturn(1);
        when(source.read(any(byte[].class))).thenThrow(new IOException("upload failed"));
        Path staged;
        try (HttpRequestBody body = new HttpRequestBody(source)) {
            assertThrows(IOException.class, body::prepareForResponse);
            staged = stagedPath(body);
        }
        verify(source).close();
        assertFalse(Files.exists(staged));
    }

    private static Path stagedPath(HttpRequestBody body) throws ReflectiveOperationException {
        Field field = HttpRequestBody.class.getDeclaredField("stagedBody");
        field.setAccessible(true);
        return (Path) field.get(body);
    }

    private static byte[] readAll(InputStream body) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = body.read(buffer)) != -1) {
            bytes.write(buffer, 0, count);
        }
        return bytes.toByteArray();
    }
}
