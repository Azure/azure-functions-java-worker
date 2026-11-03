package com.microsoft.azure.functions.worker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.HostConfiguration;
import com.microsoft.azure.functions.rpc.messages.StatusResult;
import com.microsoft.azure.functions.rpc.messages.StreamingMessage;
import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;
import com.microsoft.azure.functions.rpc.messages.WorkerInitResponse;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.handler.FunctionEnvironmentReloadRequestHandler;
import com.microsoft.azure.functions.worker.handler.WorkerInitRequestHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.microsoft.azure.functions.worker.Constants.JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY;
import static com.microsoft.azure.functions.worker.Constants.JAVA_ENABLE_OPENTELEMETRY;

class WorkerInitRequestHandlerTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @EnumSource(value = HostConfiguration.Status.class, names = {"Available", "Missing", "Error", "TooLarge"})
    void deliveryDoesNotChangeInitResultCapabilitiesOrMetadata(HostConfiguration.Status status) throws Exception {
        JavaFunctionBroker broker = mock(JavaFunctionBroker.class);
        HostConfigurationSnapshotProvider provider = provider(status);
        WorkerInitRequest baselineRequest = WorkerInitRequest.newBuilder().setWorkerDirectory("worker-install").build();
        WorkerInitResponse baseline = handle(broker, provider, baselineRequest);
        assertFalse(baseline.hasHostConfiguration());
        WorkerInitResponse delivered = handle(broker, provider,
                baselineRequest.toBuilder().putCapabilities("HostConfiguration", "1").build());
        assertTrue(delivered.hasHostConfiguration());
        assertEquals(status, delivered.getHostConfiguration().getStatus());
        assertEquals(StatusResult.Status.Success, delivered.getResult().getStatus());
        assertFalse(delivered.getResult().hasException());
        assertEquals(baseline, delivered.toBuilder().clearHostConfiguration().build());
        assertEquals("TypedDataCollection", delivered.getCapabilitiesOrThrow("TypedDataCollection"));
        assertEquals("WorkerStatus", delivered.getCapabilitiesOrThrow("WorkerStatus"));
        assertEquals("RpcHttpBodyOnly", delivered.getCapabilitiesOrThrow("RpcHttpBodyOnly"));
        assertEquals("RpcHttpTriggerMetadataRemoved", delivered.getCapabilitiesOrThrow("RpcHttpTriggerMetadataRemoved"));
        assertEquals("HandlesWorkerTerminateMessage", delivered.getCapabilitiesOrThrow("HandlesWorkerTerminateMessage"));
        assertEquals("HandlesWorkerWarmupMessage", delivered.getCapabilitiesOrThrow("HandlesWorkerWarmupMessage"));
        assertFalse(delivered.containsCapabilities("HostConfiguration"));
        assertEquals("java", delivered.getWorkerMetadata().getRuntimeName());
        assertEquals(System.getProperty("java.version"), delivered.getWorkerMetadata().getRuntimeVersion());
        assertEquals(System.getProperty("os.arch"), delivered.getWorkerMetadata().getWorkerBitness());
        if (status != HostConfiguration.Status.Available) {
            assertTrue(delivered.getHostConfiguration().getContent().isEmpty());
        }
        verify(broker, times(2)).setWorkerDirectory("worker-install");
    }

    @ParameterizedTest
    @EnumSource(value = HostConfiguration.Status.class, names = {"Available", "Missing", "Error", "TooLarge"})
    void genuineInitFailureRemainsFailure(HostConfiguration.Status status) throws Exception {
        JavaFunctionBroker broker = mock(JavaFunctionBroker.class);
        doThrow(new IllegalStateException("genuine init failure")).when(broker).setWorkerDirectory(anyString());
        WorkerInitResponse response = handle(broker, provider(status),
                WorkerInitRequest.newBuilder().putCapabilities("HostConfiguration", "1").build());
        assertEquals(StatusResult.Status.Failure, response.getResult().getStatus());
        assertTrue(response.getResult().hasException());
        assertTrue(response.getResult().getException().getMessage().contains("genuine init failure"));
        assertEquals(status, response.getHostConfiguration().getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {JAVA_ENABLE_OPENTELEMETRY, JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY})
    void telemetryCapabilitiesAndWorkerLocalEnvironmentLookupArePreserved(String setting) throws Exception {
        Map<String, String> original = new HashMap<>(System.getenv());
        Map<String, String> environment = new HashMap<>(original);
        environment.remove(JAVA_ENABLE_OPENTELEMETRY);
        environment.remove(JAVA_APPLICATIONINSIGHTS_ENABLE_TELEMETRY);
        environment.put(setting, "true");
        environment.put("FUNCTIONS_APPLICATION_DIRECTORY", directory.toString());
        Files.write(directory.resolve("host.json"), "{\"value\":\"from-file\"}".getBytes(StandardCharsets.UTF_8));
        environment.put("AzureFunctionsJobHost__value", "from-environment");
        FunctionEnvironmentReloadRequestHandler reload = new FunctionEnvironmentReloadRequestHandler(
                mock(JavaFunctionBroker.class));
        try {
            reload.setEnv(environment);
            WorkerInitResponse response = handle(mock(JavaFunctionBroker.class),
                    new HostConfigurationSnapshotProvider(), WorkerInitRequest.newBuilder()
                            .putCapabilities("HostConfiguration", "1")
                            .setFunctionAppDirectory("not-the-local-root").setWorkerDirectory("worker-install").build());
            assertEquals("true", response.getCapabilitiesOrThrow("WorkerOpenTelemetryEnabled"));
            assertEquals("true", response.getCapabilitiesOrThrow("WorkerApplicationInsightsLoggingEnabled"));
            assertEquals(StatusResult.Status.Success, response.getResult().getStatus());
            assertEquals(ByteString.copyFromUtf8("{\"value\":\"from-file\"}"), response.getHostConfiguration().getContent());
        } finally {
            reload.setEnv(original);
        }
    }

    @Test
    void configurationValuesAndPathBearingErrorsAreNotLoggedOrAddedToMetadata() {
        String secret = "sensitive-host-path-and-value";
        List<LogRecord> records = new ArrayList<>();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }
            @Override
            public void flush() { }
            @Override
            public void close() { }
        };
        Logger systemLogger = WorkerLogManager.getSystemLogger();
        Logger hostLogger = WorkerLogManager.getHostLogger();
        systemLogger.addHandler(collector);
        hostLogger.addHandler(collector);
        try {
            HostConfigurationSnapshotProvider failure = new HostConfigurationSnapshotProvider(() -> secret,
                    path -> { throw new AccessDeniedException(secret); });
            HostConfigurationSnapshotProvider success = new HostConfigurationSnapshotProvider(() -> secret,
                    path -> new ByteArrayInputStream(secret.getBytes(StandardCharsets.UTF_8)));
            WorkerInitRequest request = WorkerInitRequest.newBuilder().putCapabilities("HostConfiguration", "1").build();
            for (HostConfigurationSnapshotProvider provider : new HostConfigurationSnapshotProvider[]{failure, success}) {
                WorkerInitResponse response = handle(mock(JavaFunctionBroker.class), provider, request);
                assertEquals(StatusResult.Status.Success, response.getResult().getStatus());
                assertFalse(response.toBuilder().clearHostConfiguration().build().toString().contains(secret));
            }
            assertFalse(records.isEmpty());
            for (LogRecord record : records) {
                assertFalse(record.getMessage().contains(secret));
                assertNull(record.getThrown());
                if (record.getParameters() != null) {
                    for (Object parameter : record.getParameters()) {
                        assertFalse(String.valueOf(parameter).contains(secret));
                    }
                }
            }
        } finally {
            systemLogger.removeHandler(collector);
            hostLogger.removeHandler(collector);
        }
    }

    @Test
    void schemaAndSerializedEnvelopeMatchCanonicalWireContract() throws Exception {
        assertEquals(6, WorkerInitResponse.getDescriptor().findFieldByName("host_configuration").getNumber());
        assertEquals(1, HostConfiguration.getDescriptor().findFieldByName("status").getNumber());
        assertEquals(2, HostConfiguration.getDescriptor().findFieldByName("content").getNumber());
        assertEquals(0, HostConfiguration.Status.Unspecified.getNumber());
        assertEquals(1, HostConfiguration.Status.Available.getNumber());
        assertEquals(2, HostConfiguration.Status.Missing.getNumber());
        assertEquals(3, HostConfiguration.Status.Error.getNumber());
        assertEquals(4, HostConfiguration.Status.TooLarge.getNumber());
        WorkerInitResponse response = WorkerInitResponse.newBuilder().setHostConfiguration(
                HostConfiguration.newBuilder().setStatus(HostConfiguration.Status.Available)
                        .setContent(ByteString.copyFromUtf8("{}"))).build();
        assertArrayEquals(new byte[]{0x32, 0x06, 0x08, 0x01, 0x12, 0x02, 0x7B, 0x7D}, response.toByteArray());
        assertEquals(response, WorkerInitResponse.parseFrom(response.toByteArray()));
        assertFalse(WorkerInitResponse.parseFrom(new byte[0]).hasHostConfiguration());
    }

    private HostConfigurationSnapshotProvider provider(HostConfiguration.Status status) throws Exception {
        Path root = Files.createDirectory(directory.resolve(status.name()));
        if (status == HostConfiguration.Status.Available) {
            Files.write(root.resolve("host.json"), "{not JSON}".getBytes(StandardCharsets.UTF_8));
        } else if (status == HostConfiguration.Status.Error) {
            Files.write(root.resolve("host.json"), new byte[]{(byte) 0xFF});
        } else if (status == HostConfiguration.Status.TooLarge) {
            Files.write(root.resolve("host.json"),
                    HostConfigurationSnapshotProviderTest.repeat("x", 65_537).getBytes(StandardCharsets.UTF_8));
        }
        return new HostConfigurationSnapshotProvider(root::toString, Files::newInputStream);
    }

    private WorkerInitResponse handle(JavaFunctionBroker broker, HostConfigurationSnapshotProvider provider,
                                      WorkerInitRequest request) {
        WorkerInitRequestHandler handler = new WorkerInitRequestHandler(broker, provider);
        handler.setRequest(StreamingMessage.newBuilder().setWorkerInitRequest(request).build());
        handler.handle();
        StreamingMessage.Builder response = StreamingMessage.newBuilder();
        handler.marshalResponse(response);
        return response.getWorkerInitResponse();
    }
}
