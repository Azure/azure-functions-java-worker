package com.microsoft.azure.functions.worker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.HostConfiguration;
import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Supplier;

/**
 * Internal, worker-lifetime snapshot of host.json. Only an enabled init captures it;
 * all outcomes are immutable until the worker is restarted.
 */
public final class HostConfigurationSnapshotProvider {
    static final int MAX_CONTENT_BYTES = 65_536;
    private final Supplier<String> applicationDirectory;
    private final FileOpener fileOpener;
    private HostConfiguration snapshot;

    public HostConfigurationSnapshotProvider() {
        this(() -> System.getenv("FUNCTIONS_APPLICATION_DIRECTORY"), Files::newInputStream);
    }

    HostConfigurationSnapshotProvider(Supplier<String> applicationDirectory, FileOpener fileOpener) {
        this.applicationDirectory = applicationDirectory;
        this.fileOpener = fileOpener;
    }

    public synchronized HostConfiguration getConfiguration(WorkerInitRequest request) {
        if (!"1".equals(request.getCapabilitiesMap().get("HostConfiguration"))) {
            return null;
        }
        if (snapshot == null) {
            snapshot = capture();
        }
        return HostConfiguration.newBuilder().setStatus(snapshot.getStatus())
                .setContent(snapshot.getContent()).build();
    }

    private HostConfiguration capture() {
        try {
            String root = applicationDirectory.get();
            if (root == null || root.codePoints().allMatch(c ->
                    Character.isWhitespace(c) || Character.isSpaceChar(c) || c == 0x85)) {
                return outcome(HostConfiguration.Status.Error);
            }
            Path path = Paths.get(root).resolve("host.json");
            InputStream stream;
            try {
                stream = fileOpener.open(path);
            } catch (NoSuchFileException ex) {
                // NIO uses the same exception for an absent file and an absent parent.
                // Only inspect the parent after an unsuccessful open, never before a read.
                return outcome(Files.readAttributes(path.getParent(), BasicFileAttributes.class).isDirectory()
                        ? HostConfiguration.Status.Missing : HostConfiguration.Status.Error);
            }
            try (InputStream input = stream) {
                return readBounded(input);
            }
        } catch (ContentTooLargeException ex) {
            return outcome(HostConfiguration.Status.TooLarge);
        } catch (IOException | RuntimeException ex) {
            // These are delivery failures, not initialization failures. Never expose exception paths/values.
            return outcome(HostConfiguration.Status.Error);
        }
    }

    private static HostConfiguration readBounded(InputStream input) throws IOException {
        PushbackInputStream stream = new PushbackInputStream(input, 4);
        byte[] head = new byte[4];
        int count = 0;
        while (count < head.length) {
            int n = stream.read(head, count, head.length - count);
            if (n == -1) {
                break;
            }
            count += n;
        }

        Charset encoding = StandardCharsets.UTF_8;
        int skip = 0;
        // The UTF-32 decoders consume their own BOM. Leave it in the stream so a
        // second U+FEFF in the content is not accidentally stripped as another BOM.
        if (count == 4 && head[0] == (byte) 0xFF && head[1] == (byte) 0xFE && head[2] == 0 && head[3] == 0) {
            encoding = Charset.forName("UTF-32LE");
        } else if (count == 4 && head[0] == 0 && head[1] == 0 && head[2] == (byte) 0xFE && head[3] == (byte) 0xFF) {
            encoding = Charset.forName("UTF-32BE");
        } else if (count >= 3 && head[0] == (byte) 0xEF && head[1] == (byte) 0xBB && head[2] == (byte) 0xBF) {
            skip = 3;
        } else if (count >= 2 && head[0] == (byte) 0xFF && head[1] == (byte) 0xFE) {
            encoding = StandardCharsets.UTF_16LE;
            skip = 2;
        } else if (count >= 2 && head[0] == (byte) 0xFE && head[1] == (byte) 0xFF) {
            encoding = StandardCharsets.UTF_16BE;
            skip = 2;
        }
        stream.unread(head, skip, count - skip);

        Reader reader = new InputStreamReader(stream, encoding.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
        BoundedContent content = new BoundedContent();
        try (Writer writer = new OutputStreamWriter(content, StandardCharsets.UTF_8.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            char[] chars = new char[4096];
            int length;
            while ((length = reader.read(chars)) != -1) {
                writer.write(chars, 0, length);
            }
        }
        return HostConfiguration.newBuilder().setStatus(HostConfiguration.Status.Available)
                .setContent(ByteString.copyFrom(content.bytes.toByteArray())).build();
    }

    private static HostConfiguration outcome(HostConfiguration.Status status) {
        return HostConfiguration.newBuilder().setStatus(status).build();
    }

    @FunctionalInterface
    interface FileOpener {
        InputStream open(Path path) throws IOException;
    }

    private static final class BoundedContent extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void write(int value) throws IOException {
            if (bytes.size() == MAX_CONTENT_BYTES) {
                throw new ContentTooLargeException();
            }
            bytes.write(value);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (length > MAX_CONTENT_BYTES - bytes.size()) {
                throw new ContentTooLargeException();
            }
            bytes.write(buffer, offset, length);
        }
    }

    private static final class ContentTooLargeException extends IOException {
        private static final long serialVersionUID = 1L;
    }
}
