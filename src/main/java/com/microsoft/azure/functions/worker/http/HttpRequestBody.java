package com.microsoft.azure.functions.worker.http;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Live request stream during function execution. Unread bytes are staged on disk
 * before sending the response because the host's HTTP/1.1 forwarder finishes
 * uploading the request before it reads the response. Reading and writing both
 * sockets simultaneously can otherwise deadlock once their buffers fill.
 */
public final class HttpRequestBody extends FilterInputStream {
    private Path stagedBody;
    private boolean closed;
    private final InputStream userStream;

    public HttpRequestBody(InputStream source) {
        super(source);
        userStream = new FilterInputStream(this) {
            private boolean userClosed;

            private void ensureOpen() throws IOException {
                if (userClosed) {
                    throw new IOException("Request body is closed");
                }
            }

            @Override
            public int read() throws IOException {
                ensureOpen();
                return super.read();
            }

            @Override
            public int read(byte[] bytes, int offset, int length) throws IOException {
                ensureOpen();
                return super.read(bytes, offset, length);
            }

            @Override
            public long skip(long count) throws IOException {
                ensureOpen();
                return super.skip(count);
            }

            @Override
            public int available() throws IOException {
                ensureOpen();
                return super.available();
            }

            @Override
            public void close() {
                // The transport owner must still drain the upload before responding.
                userClosed = true;
            }
        };
    }

    public InputStream getStream() {
        return userStream;
    }

    /**
     * Keeps any remaining request bytes available to a deferred response writer,
     * without retaining the entire upload in memory.
     */
    public void prepareForResponse() throws IOException {
        if (closed || stagedBody != null) {
            return;
        }
        int first = in.read();
        if (first == -1) {
            return;
        }
        stagedBody = Files.createTempFile("functions-http-request-", ".tmp");
        try (OutputStream staged = Files.newOutputStream(stagedBody)) {
            staged.write(first);
            byte[] buffer = new byte[8192];
            int count;
            while ((count = in.read(buffer)) != -1) {
                staged.write(buffer, 0, count);
            }
        }
        in.close();
        in = Files.newInputStream(stagedBody, StandardOpenOption.DELETE_ON_CLOSE);
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        try {
            in.close();
        } catch (IOException ex) {
            failure = ex;
        }
        try {
            if (stagedBody != null) {
                Files.deleteIfExists(stagedBody);
            }
        } catch (IOException ex) {
            if (failure == null) {
                failure = ex;
            } else {
                failure.addSuppressed(ex);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
