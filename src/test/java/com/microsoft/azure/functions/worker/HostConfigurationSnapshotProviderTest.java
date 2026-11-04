package com.microsoft.azure.functions.worker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.HostConfiguration;
import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class HostConfigurationSnapshotProviderTest {
    private static final WorkerInitRequest ENABLED = WorkerInitRequest.newBuilder()
            .putCapabilities("HostConfiguration", "1").build();
    @TempDir
    Path directory;
    private final List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Handler collector = new Handler() {
        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }
        @Override
        public void flush() { }
        @Override
        public void close() { }
    };

    @BeforeEach
    void captureDiagnostics() {
        WorkerLogManager.getSystemLogger().addHandler(collector);
    }

    @AfterEach
    void removeDiagnosticCapture() {
        WorkerLogManager.getSystemLogger().removeHandler(collector);
    }

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
        assertTrue(records.isEmpty(), "Disabled requests and empty success must not log");
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
        Files.write(directory.resolve("host.json"), "\"local\"".getBytes(StandardCharsets.UTF_8));
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
        assertEquals(ByteString.copyFromUtf8("\"local\""), result.getContent());
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
    @ValueSource(strings = {"valid", "malformedJson", "invalidEncoding", "oversized"})
    void closeFailureAlwaysRemainsReadFailed(String source) {
        byte[] content = "oversized".equals(source) ? repeat("x", 65_537).getBytes(StandardCharsets.UTF_8)
                : "invalidEncoding".equals(source) ? bytes(0xFF)
                : ("malformedJson".equals(source) ? "{invalid" : "{}").getBytes(StandardCharsets.UTF_8);
        InputStream stream = new ByteArrayInputStream(content) {
            @Override
            public void close() throws IOException {
                throw new NoSuchFileException("sensitive path");
            }
        };
        assertOutcome(HostConfiguration.Status.Error, provider(stream).getConfiguration(ENABLED));
        assertReason("ReadFailed");
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void strictBomConversionSurvivesShortReadsAndSurrogateBoundaries(SourceEncoding encoding) {
        String text = "\"" + repeat("x", 4094) + "\uD83D\uDE00\u20AC\u00E9"
                + repeat("x", 4095) + "\uD83D\uDE00\"";
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
    void emptyAndSyntacticallyValidContentRemainForHostInterpretation(SourceEncoding encoding) {
        for (String text : Arrays.asList("", "null", "{\"version\":\"wrong\",\"value\":1}")) {
            HostConfiguration result = provider(encoding.encode(text)).getConfiguration(ENABLED);
            assertEquals(HostConfiguration.Status.Available, result.getStatus());
            assertEquals(ByteString.copyFromUtf8(text), result.getContent());
        }
        assertTrue(records.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = SourceEncoding.class, names = "UTF8", mode = EnumSource.Mode.EXCLUDE)
    void aSecondBomIsContentAndNotValidJson(SourceEncoding encoding) {
        assertOutcome(HostConfiguration.Status.Error,
                provider(encoding.encode("\uFEFF{}")).getConfiguration(ENABLED));
        assertReason("InvalidJson");
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void finalUtf8LimitIsInclusiveAndIndependentOfSourceLength(SourceEncoding encoding) {
        String atLimit = "\"" + repeat("x", 65_534) + "\"";
        HostConfiguration result = provider(encoding.encode(atLimit)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(65_536, result.getContent().size());
        assertEquals(ByteString.copyFromUtf8(atLimit), result.getContent());
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider(encoding.encode(atLimit + " ")).getConfiguration(ENABLED));

        String expanded = "\"" + repeat("\u20AC", 21_844) + "xx\"";
        assertEquals(65_536, expanded.getBytes(StandardCharsets.UTF_8).length);
        result = provider(encoding.encode(expanded)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(ByteString.copyFromUtf8(expanded), result.getContent());
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider(encoding.encode(expanded + " ")).getConfiguration(ENABLED));
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void commentsTrailingCommasAndWhitespaceNormalizeWithoutChangingTokens(SourceEncoding encoding) {
        String expected = "{\"version\":\"2.0\",\"\\u0061\":\"x\\u0041\\n\\/\\\\\\\"\","
                + "\"a\":[1E+2,-0.50,-0,1e999,123456789012345678901234567890,"
                + "0.12345678901234567890123456789,true,false,null,{},[]],"
                + "\"a\":\"\u20AC\uD83D\uDE00\",\"surrogates\":\"\\uD800\\uDE00\\uD800\","
                + "\"commentText\":\"/* kept */ // kept\","
                + "\"e\":{}}";
        String source = "// header\r\n" + expected
                .replace("[1E+2", "[ /* values */ 1E+2")
                .replace(",\"e\":{}", ",/* after string */\"e\":{ },")
                .replace("null,{},[]]", "null,{},[],/* trailing array comma */]") + " /* footer */ // eof";
        HostConfiguration result = provider(encoding.encode(source)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(ByteString.copyFromUtf8(expected), result.getContent());
        assertTrue(records.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "true", "false", "-0", "1E+2", "1e999", "\"text\"", "{}", "[]",
            "{\"a\":1,\"a\":2}", "\"\\uD800\"", "\"\\uDC00\"", "\"\\u0000\"",
            "{\"\\uDC00\\uD800\":\"\\uDC00\\uD800\"}",
            "[1,2,]", "{\"x\":[{},],}", "[ /*comment*/ ]"})
    void acceptsAnyRootAndOnlyRemovesAllowedTrivia(String source) {
        HostConfiguration result = provider((" /* prefix */ " + source + "\n// trailing")
                .getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(ByteString.copyFromUtf8(source.replace(",]", "]").replace(",}", "}")
                .replace(" /*comment*/ ", "")), result.getContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "\t\r\n", "//comment", "/*comment*/", "{invalid json", "{",
            "[] {}", "1 2", "1/*comment*/2", "[1/*comment*/2]", "truefalse", "[true false]",
            "[,]", "{,}", "[1,,]", "{\"x\":,}", "{\"x\":1,,}", "{\"x\",}", "{\"x\":1,",
            "\"unterminated", "\"\\q\"", "\"\\u123\"", "\"\\uQQQQ\"", "\"control\ncharacter\"",
            "\"control\tcharacter\"", "'single'", "{unquoted:1}", "{\"x\"=1}", "[1;2]", "[1,,2]",
            "NaN", "Infinity", "-Infinity", "+1", "01", "-01", "1.", ".1", "1e", "1e+", "--1",
            "0x12", "True", "NULL", "undefined", "()",
            "{} /*unterminated", "{} /", "{} #comment", "#comment\n{}", "\uFEFF\uFEFF{}", "\u000B{}",
            "{}\u00A0", "{}\u2028", "{}\u2029", "{}\u0000", "{\"x\":1},", "[1,] garbage",
            "{}//\u2028", "{}//\u2029", "//start\u2028\n{}", "[1//comment\u2029\n]"})
    void malformedOrOverlyLenientJsonIsContentFreeError(String source) {
        HostConfigurationSnapshotProvider provider = provider(source.getBytes(StandardCharsets.UTF_8));
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertReason("InvalidJson");
    }

    @ParameterizedTest
    @EnumSource(SourceEncoding.class)
    void literalReplacementCharacterAndUnicodeAreNotEncodingErrors(SourceEncoding encoding) {
        String json = "{\"text\":\"\uFFFD\uFEFF\u2028\u2029\uD83D\uDE00\"}";
        HostConfiguration result = provider(encoding.encode(json)).getConfiguration(ENABLED);
        assertEquals(HostConfiguration.Status.Available, result.getStatus());
        assertEquals(ByteString.copyFromUtf8(json), result.getContent());
        assertTrue(records.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/*\u2028\u2029*/{}", "{}/*\u2028\u2029*/", "// comment\r{}", "// comment\n{}",
            "{}// eof", "{/*\u0000*/}", "{}//\uFFFD"})
    void commentsUseAsciiLineEndingsAndBlockCommentsAllowUnicodeSeparators(String source) {
        assertEquals(ByteString.copyFromUtf8("{}"),
                provider(source.getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED).getContent());
        assertTrue(records.isEmpty());
    }

    @Test
    void nestingDepthIsLimitedTo64Containers() {
        for (String[] container : new String[][]{{"[", "]"}, {"{\"x\":", "}"}}) {
            String boundary = repeat(container[0], 64) + "0" + repeat(container[1], 64);
            assertEquals(ByteString.copyFromUtf8(boundary),
                    provider(boundary.getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED).getContent());
            assertOutcome(HostConfiguration.Status.Error,
                    provider((container[0] + boundary + container[1]).getBytes(StandardCharsets.UTF_8))
                            .getConfiguration(ENABLED));
        }
    }

    @Test
    void longNumbersAndExponentsArePreservedWithoutNativeNumericConversion() {
        for (int length : new int[]{1024, 2048, 8192, 65_536}) {
            for (String json : new String[]{repeat("9", length), "1e+" + repeat("9", length - 3)}) {
                HostConfiguration result = provider(json.getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED);
                assertEquals(HostConfiguration.Status.Available, result.getStatus());
                assertEquals(ByteString.copyFromUtf8(json), result.getContent());
            }
        }
        assertTrue(records.isEmpty());
    }

    @Test
    void convertedLimitIsCheckedBeforeParsingOrCompacting() {
        String source = "{}/*" + repeat("x", 65_530) + "*/";
        assertEquals(65_536, source.length());
        assertEquals(ByteString.copyFromUtf8("{}"),
                provider(source.getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED).getContent());
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider((source + " ").getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED));
        assertOutcome(HostConfiguration.Status.TooLarge,
                provider(repeat("?", 65_537).getBytes(StandardCharsets.UTF_8)).getConfiguration(ENABLED));
    }

    @ParameterizedTest
    @MethodSource("invalidEncodings")
    void invalidAndTruncatedEncodingsReturnContentFreeError(byte[] source) {
        HostConfigurationSnapshotProvider provider = provider(source);
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertOutcome(HostConfiguration.Status.Error, provider.getConfiguration(ENABLED));
        assertReason("InvalidEncoding");
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
            Files.write(file, "{invalid json".getBytes(StandardCharsets.UTF_8));
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
        if (status == HostConfiguration.Status.Error) {
            assertReason("InvalidJson");
        } else if (status == HostConfiguration.Status.TooLarge) {
            assertReason("Oversized");
        } else {
            assertTrue(records.isEmpty(), "Available and Missing must not warn");
        }
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
            if (fail) {
                assertReason("OpenFailed");
            } else {
                assertTrue(records.isEmpty());
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"AppDirectoryUnavailable", "AppDirectoryNotSet", "AppDirectoryInvalid",
            "AppDirectoryNotFound", "AccessDenied", "OpenFailed", "ReadFailed", "InvalidEncoding",
            "InvalidJson", "Oversized"})
    void fixedFailureReasonIsLoggedOnceAndNeverForDisabledRequests(String reason) {
        String secret = "sensitive-host-path-and-value";
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> {
            switch (reason) {
                case "AppDirectoryUnavailable": throw new SecurityException(secret);
                case "AppDirectoryNotSet": return " ";
                case "AppDirectoryInvalid": return "\u0000" + secret;
                case "AppDirectoryNotFound": return directory.resolve("absent").toString();
                default: return directory.toString();
            }
        }, path -> {
            switch (reason) {
                case "AppDirectoryNotFound": throw new NoSuchFileException(secret);
                case "AccessDenied": throw new AccessDeniedException(secret);
                case "OpenFailed": throw new IOException(secret);
                case "ReadFailed": return new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)) {
                    @Override
                    public void close() throws IOException {
                        throw new NoSuchFileException(secret);
                    }
                };
                case "InvalidEncoding": return new ByteArrayInputStream(bytes(0xFF));
                case "InvalidJson": return new ByteArrayInputStream(("{\"" + secret + "\":}")
                        .getBytes(StandardCharsets.UTF_8));
                case "Oversized": return new ByteArrayInputStream(repeat("x", 65_537)
                        .getBytes(StandardCharsets.UTF_8));
                default: throw new AssertionError("Invalid root must not open");
            }
        });
        assertNull(provider.getConfiguration(WorkerInitRequest.getDefaultInstance()));
        assertNull(provider.getConfiguration(WorkerInitRequest.newBuilder().putCapabilities("hostconfiguration", "1").build()));
        assertNull(provider.getConfiguration(WorkerInitRequest.newBuilder().putCapabilities("HostConfiguration", "true").build()));
        assertTrue(records.isEmpty());
        HostConfiguration first = provider.getConfiguration(ENABLED);
        assertOutcome("Oversized".equals(reason) ? HostConfiguration.Status.TooLarge : HostConfiguration.Status.Error, first);
        assertEquals(first, provider.getConfiguration(ENABLED));
        assertNull(provider.getConfiguration(WorkerInitRequest.getDefaultInstance()));
        assertReason(reason);
    }

    private void assertReason(String reason) {
        assertEquals(1, records.size());
        LogRecord record = records.get(0);
        assertEquals(Level.WARNING, record.getLevel());
        assertEquals("Host configuration could not be delivered to the host. Reason: " + reason
                + ". The failure is retained until the worker process restarts.", record.getMessage());
        assertNull(record.getThrown());
        assertNull(record.getParameters());
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
