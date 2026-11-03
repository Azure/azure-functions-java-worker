package com.microsoft.azure.functions.worker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.HostConfiguration;
import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HostConfigurationSnapshotProviderTest {
    private static final WorkerInitRequest ENABLED = WorkerInitRequest.newBuilder()
            .putCapabilities("HostConfiguration", "1").build();
    @TempDir
    Path directory;

    @Test
    void onlyExactGateCapturesAndDisabledRequestsNeverAccessRootOrFile() {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> {
            lookups.incrementAndGet();
            return directory.toString();
        }, path -> {
            opens.incrementAndGet();
            return new ByteArrayInputStream(new byte[0]);
        });
        List<WorkerInitRequest> disabled = new ArrayList<>();
        disabled.add(WorkerInitRequest.getDefaultInstance());
        disabled.add(WorkerInitRequest.newBuilder().putCapabilities("hostconfiguration", "1").build());
        disabled.add(WorkerInitRequest.newBuilder().putCapabilities("HOSTCONFIGURATION", "1").build());
        for (String value : Arrays.asList("", "0", "2", "true", "01", " 1", "1 ", "1.0")) {
            disabled.add(WorkerInitRequest.newBuilder().putCapabilities("HostConfiguration", value).build());
        }
        for (WorkerInitRequest request : disabled) {
            assertNull(provider.getConfiguration(request));
        }
        assertEquals(0, lookups.get());
        assertEquals(0, opens.get());
        assertOutcome(HostConfiguration.Status.Available, provider.getConfiguration(ENABLED));
        for (WorkerInitRequest request : disabled) {
            assertNull(provider.getConfiguration(request));
        }
        assertEquals(1, lookups.get());
        assertEquals(1, opens.get());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\r\n", "\u2003", "\u00A0", "\u0085", "\u0000"})
    void invalidRootIsCachedErrorWithoutFileAccess(String root) {
        AtomicReference<String> currentRoot = new AtomicReference<>(root);
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(currentRoot::get,
                path -> { throw new AssertionError("Invalid roots must not open a file"); });
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        currentRoot.set(directory.toString());
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
    }

    @Test
    void rootAccessorFailureIsCached() {
        AtomicInteger calls = new AtomicInteger();
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> {
            calls.incrementAndGet();
            throw new SecurityException("sensitive path and configuration value");
        }, path -> { throw new AssertionError("Root lookup failed"); });
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertEquals(1, calls.get());
    }

    @Test
    void usesOnlyWorkerLocalRootAndPreservesNativeRelativeResolution() throws Exception {
        Files.write(directory.resolve("host.json"), "local".getBytes(StandardCharsets.UTF_8));
        Path child = Files.createDirectory(directory.resolve("child"));
        Path relative = Paths.get("").toAbsolutePath().relativize(child).resolve("..");
        AtomicReference<Path> opened = new AtomicReference<>();
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(relative::toString, path -> {
            opened.set(path);
            return Files.newInputStream(path);
        });
        HostConfiguration result = provider.getConfiguration(ENABLED.toBuilder()
                .setFunctionAppDirectory("not-the-local-root").setWorkerDirectory("not-the-local-root").build());
        assertEquals(relative.resolve("host.json"), opened.get());
        assertEquals(ByteString.copyFromUtf8("local"), result.getContent());
    }

    @Test
    void doesNotTrimANonblankRootOrPreflightSuccessfulReads() {
        String root = " application directory";
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> root, path -> {
            assertEquals(Paths.get(root).resolve("host.json"), path);
            return new ByteArrayInputStream(new byte[0]);
        });
        assertOutcome(HostConfiguration.Status.Available, provider.getConfiguration(ENABLED));
    }

    @Test
    void missingFileIsDistinctFromMissingOrInvalidParent() throws Exception {
        assertOutcome(HostConfiguration.Status.Missing, physical(directory).getConfiguration(ENABLED));
        assertOutcome(HostConfiguration.Status.Error, physical(directory.resolve("absent")).getConfiguration(ENABLED));
        Path file = Files.write(directory.resolve("not-a-directory"), new byte[0]);
        assertOutcome(HostConfiguration.Status.Error, physical(file).getConfiguration(ENABLED));
        Files.createDirectory(directory.resolve("host.json"));
        assertOutcome(HostConfiguration.Status.Error, physical(directory).getConfiguration(ENABLED));
    }

    @Test
    void permissionAndReadFailuresAreContentFreeErrors() {
        for (IOException failure : Arrays.asList(new AccessDeniedException("sensitive-path"),
                new IOException("sensitive-path"), new java.io.FileNotFoundException("ambiguous open failure"))) {
            HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(directory::toString,
                    path -> { throw failure; });
            assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        }
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(directory::toString,
                path -> { throw new SecurityException("sensitive-path"); });
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
    }

    @Test
    void midReadAndCloseFailuresDoNotBecomeMissingOrPartialSuccess() {
        InputStream brokenRead = new InputStream() {
            private int count;
            @Override
            public int read() throws IOException {
                if (count++ < 4) {
                    return 'x';
                }
                throw new NoSuchFileException("sensitive-path");
            }
        };
        assertOutcome(HostConfiguration.Status.Error, provider(brokenRead).getConfiguration(ENABLED));
        InputStream brokenClose = new ByteArrayInputStream(new byte[0]) {
            @Override
            public void close() throws IOException {
                throw new IOException("sensitive-path");
            }
        };
        assertOutcome(HostConfiguration.Status.Error, provider(brokenClose).getConfiguration(ENABLED));
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void strictBomConversionSurvivesShortReadsAndSurrogateBoundaries(SourceEncoding encoding) {
        String text = repeat("x", 4095) + "\uD83D\uDE00\u20AC\u00E9" + repeat("x", 4095) + "\uD83D\uDE00";
        byte[] source = encoding.encode(text);
        for (int chunkSize : new int[]{1, 3, 4096}) {
            InputStream stream = new ByteArrayInputStream(source) {
                @Override
                public synchronized int read(byte[] b, int offset, int length) {
                    return super.read(b, offset, Math.min(chunkSize, length));
                }
            };
            HostConfiguration result = provider(stream).getConfiguration(ENABLED);
            assertEquals(HostConfiguration.Status.Available, result.getStatus());
            assertEquals(ByteString.copyFromUtf8(text), result.getContent());
        }
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void emptyAndMalformedJsonAreTransportedWithoutParsing(SourceEncoding encoding) {
        for (String text : Arrays.asList("", "{invalid json", "null", "{\"version\":\"wrong\",\"value\":1}")) {
            HostConfiguration result = provider(encoding.encode(text)).getConfiguration(ENABLED);
            assertEquals(HostConfiguration.Status.Available, result.getStatus());
            assertEquals(ByteString.copyFromUtf8(text), result.getContent());
        }
    }

    @ParameterizedTest
    @EnumSource(value = SourceEncoding.class, names = "UTF8", mode = EnumSource.Mode.EXCLUDE)
    void removesExactlyOneBom(SourceEncoding encoding) {
        assertEquals(ByteString.copyFromUtf8("\uFEFF{}"),
                provider(encoding.encode("\uFEFF{}")).getConfiguration(ENABLED).getContent());
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void finalUtf8LimitIsInclusiveAndIndependentOfSourceLength(SourceEncoding encoding) {
        String atLimit = repeat("x", 65_536);
        HostConfiguration result = provider(encoding.encode(atLimit)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(65_536, result.getContent().size());
        assertEquals(ByteString.copyFromUtf8(atLimit), result.getContent());
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider(encoding.encode(atLimit + "x")).getConfiguration(ENABLED));

        String expanded = repeat("\u20AC", 21_845) + "x";
        assertEquals(65_536, expanded.getBytes(StandardCharsets.UTF_8).length);
        result = provider(encoding.encode(expanded)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(ByteString.copyFromUtf8(expanded), result.getContent());
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider(encoding.encode(expanded + "x")).getConfiguration(ENABLED));
    }

    @ParameterizedTest
    @MethodSource("invalidEncodings")
    void invalidAndTruncatedEncodingsReturnContentFreeError(byte[] source) {
        assertOutcome(HostConfiguration.Status.Error, provider(source).getConfiguration(ENABLED));
    }

    static Stream<byte[]> invalidEncodings() {
        return Stream.of(
                bytes(0xFF), bytes(0xC0, 0xAF), bytes(0xED, 0xA0, 0x80), bytes(0xF4, 0x90, 0x80, 0x80),
                bytes(0xEF, 0xBB), bytes(0xE2, 0x82), bytes(0xEF, 0xBB, 0xBF, 0xFF),
                bytes(0xFF, 0xFE, 0x61), bytes(0xFE, 0xFF, 0x00),
                bytes(0xFF, 0xFE, 0x00, 0xD8), bytes(0xFE, 0xFF, 0xDC, 0x00),
                bytes(0xFF, 0xFE, 0x00, 0x00, 0x61),
                bytes(0x00, 0x00, 0xFE, 0xFF, 0x00, 0x00, 0x00),
                bytes(0xFF, 0xFE, 0x00, 0x00, 0x00, 0xD8, 0x00, 0x00),
                bytes(0x00, 0x00, 0xFE, 0xFF, 0x00, 0x11, 0x00, 0x00),
                concat(repeat("x", 8192).getBytes(StandardCharsets.UTF_8), bytes(0xE2, 0x82)));
    }

    @Test
    void oversizedStreamsAreBoundedAndClosedWithoutReadingToEnd() {
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        InputStream infinite = new InputStream() {
            @Override
            public int read() {
                reads.incrementAndGet();
                return 'x';
            }
            @Override
            public void close() {
                closes.incrementAndGet();
            }
        };
        assertOutcome(HostConfiguration.Status.TooLarge, provider(infinite).getConfiguration(ENABLED));
        assertTrue(reads.get() < 100_000, "Conversion must stop at a bounded distance beyond the limit");
        assertEquals(1, closes.get());
    }

    @ParameterizedTest
    @EnumSource(value = HostConfiguration.Status.class, names = {"Available", "Missing", "Error", "TooLarge"})
    void allOutcomesAreCapturedOnceAndReplayedImmutably(HostConfiguration.Status status) throws Exception {
        Path file = directory.resolve("host.json");
        if (status == HostConfiguration.Status.Available) {
            Files.write(file, "{}".getBytes(StandardCharsets.UTF_8));
        } else if (status == HostConfiguration.Status.Error) {
            Files.write(file, bytes(0xFF));
        } else if (status == HostConfiguration.Status.TooLarge) {
            Files.write(file, repeat("x", 65_537).getBytes(StandardCharsets.UTF_8));
        }
        HostConfigurationSnapshotProvider provider = physical(directory);
        HostConfiguration first = provider.getConfiguration(ENABLED);
        assertEquals(status, first.getStatus());
        Files.write(file, "{\"changed\":true}".getBytes(StandardCharsets.UTF_8));
        HostConfiguration second = provider.getConfiguration(ENABLED);
        assertEquals(first, second);
        assertNotSame(first, second);
        assertEquals(first, provider.getConfiguration(ENABLED).toBuilder().build());
        first.toBuilder().setStatus(HostConfiguration.Status.Unspecified).clearContent().build();
        assertEquals(second, provider.getConfiguration(ENABLED));
        assertEquals(ByteString.copyFromUtf8("{\"changed\":true}"),
                physical(directory).getConfiguration(ENABLED).getContent());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentRequestsShareExactlyOneCaptureIncludingFailures(boolean fail) throws Exception {
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> {
            lookups.incrementAndGet();
            return directory.toString();
        }, path -> {
            opens.incrementAndGet();
            if (fail) {
                throw new IOException("sensitive");
            }
            return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8));
        });
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<HostConfiguration>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 32; i++) {
                results.add(executor.submit(() -> {
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return provider.getConfiguration(ENABLED);
                }));
            }
            start.countDown();
            HostConfiguration first = results.get(0).get(10, TimeUnit.SECONDS);
            assertEquals(fail ? HostConfiguration.Status.Error : HostConfiguration.Status.Available, first.getStatus());
            for (int i = 1; i < results.size(); i++) {
                HostConfiguration next = results.get(i).get(10, TimeUnit.SECONDS);
                assertEquals(first, next);
                assertNotSame(first, next);
            }
            assertEquals(1, lookups.get());
            assertEquals(1, opens.get());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private HostConfigurationSnapshotProvider physical(Path root) {
        return new HostConfigurationSnapshotProvider(root::toString, Files::newInputStream);
    }

    private HostConfigurationSnapshotProvider provider(byte[] bytes) {
        return provider(new ByteArrayInputStream(bytes));
    }

    private HostConfigurationSnapshotProvider provider(InputStream stream) {
        return new HostConfigurationSnapshotProvider(directory::toString, path -> stream);
    }

    private static void assertOutcome(HostConfiguration.Status status, HostConfiguration result) {
        assertEquals(status, result.getStatus());
        assertTrue(result.getContent().isEmpty());
    }

    static String repeat(String value, int count) {
        StringBuilder text = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            text.append(value);
        }
        return text.toString();
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static byte[] concat(byte[] prefix, byte[] content) {
        byte[] result = Arrays.copyOf(prefix, prefix.length + content.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        return result;
    }

    enum SourceEncoding {
        UTF8("UTF-8", bytes()),
        UTF8_BOM("UTF-8", bytes(0xEF, 0xBB, 0xBF)),
        UTF16_LE("UTF-16LE", bytes(0xFF, 0xFE)),
        UTF16_BE("UTF-16BE", bytes(0xFE, 0xFF)),
        UTF32_LE("UTF-32LE", bytes(0xFF, 0xFE, 0x00, 0x00)),
        UTF32_BE("UTF-32BE", bytes(0x00, 0x00, 0xFE, 0xFF));

        private final Charset charset;
        private final byte[] bom;

        SourceEncoding(String charset, byte[] bom) {
            this.charset = Charset.forName(charset);
            this.bom = bom;
        }

        byte[] encode(String text) {
            return concat(bom, text.getBytes(charset));
        }
    }
}
