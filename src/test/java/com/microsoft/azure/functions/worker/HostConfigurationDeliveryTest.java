package com.microsoft.azure.functions.worker;

import com.google.protobuf.ByteString;
import com.microsoft.azure.functions.rpc.messages.FunctionRpcGrpc;
import com.microsoft.azure.functions.rpc.messages.HostConfiguration;
import com.microsoft.azure.functions.rpc.messages.StatusResult;
import com.microsoft.azure.functions.rpc.messages.StreamingMessage;
import com.microsoft.azure.functions.rpc.messages.WorkerInitRequest;
import com.microsoft.azure.functions.rpc.messages.WorkerInitResponse;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HostConfigurationDeliveryTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"available", "openFailure", "invalidJson"})
    void clientSharesLazySnapshotAcrossRecreatedConcurrentHandlers(String outcome) throws Exception {
        boolean fail = !"available".equals(outcome);
        BlockingQueue<StreamObserver<StreamingMessage>> streams = new LinkedBlockingQueue<>();
        BlockingQueue<StreamingMessage> replies = new LinkedBlockingQueue<>();
        AtomicReference<Throwable> streamFailure = new AtomicReference<>();
        Server server = ServerBuilder.forPort(0).addService(new FunctionRpcGrpc.FunctionRpcImplBase() {
            @Override
            public StreamObserver<StreamingMessage> eventStream(StreamObserver<StreamingMessage> outbound) {
                streams.add(outbound);
                return new StreamObserver<StreamingMessage>() {
                    @Override
                    public void onNext(StreamingMessage message) {
                        if (message.hasWorkerInitResponse()) {
                            replies.add(message);
                        }
                    }
                    @Override
                    public void onError(Throwable error) {
                        streamFailure.set(error);
                    }
                    @Override
                    public void onCompleted() {
                        outbound.onCompleted();
                    }
                };
            }
        }).build().start();
        AtomicInteger lookups = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        AtomicReference<String> source = new AtomicReference<>("{ /*comment*/ \"value\": \"original\", }");
        HostConfigurationSnapshotProvider provider = new HostConfigurationSnapshotProvider(() -> {
            lookups.incrementAndGet();
            return directory.toString();
        }, path -> {
            opens.incrementAndGet();
            if ("openFailure".equals(outcome) && source.get().contains("original")) {
                throw new IOException("sensitive path");
            }
            if ("invalidJson".equals(outcome) && source.get().contains("original")) {
                return new ByteArrayInputStream("{malformed json".getBytes(StandardCharsets.UTF_8));
            }
            return new ByteArrayInputStream(source.get().getBytes(StandardCharsets.UTF_8));
        });
        IApplication app = mock(IApplication.class);
        when(app.getHost()).thenReturn("localhost");
        when(app.getPort()).thenReturn(server.getPort());
        try (JavaWorkerClient client = new JavaWorkerClient(app, provider)) {
            client.listen("configuration-worker", "start");
            StreamObserver<StreamingMessage> stream = streams.poll(10, TimeUnit.SECONDS);
            assertNotNull(stream);
            stream.onNext(request("disabled", false));
            WorkerInitResponse baseline = take(replies).getWorkerInitResponse();
            assertFalse(baseline.hasHostConfiguration());
            assertEquals(0, lookups.get());
            assertEquals(0, opens.get());
            stream.onNext(request("first", true));
            HostConfiguration first = take(replies).getWorkerInitResponse().getHostConfiguration();
            assertEquals(fail ? HostConfiguration.Status.Error : HostConfiguration.Status.Available, first.getStatus());
            assertEquals(fail ? ByteString.EMPTY : ByteString.copyFromUtf8("{\"value\":\"original\"}"), first.getContent());
            source.set("{\"value\":\"changed\"}");
            for (int i = 0; i < 16; i++) {
                stream.onNext(request("retry-" + i, true));
            }
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 16; i++) {
                StreamingMessage reply = take(replies);
                assertTrue(ids.add(reply.getRequestId()));
                WorkerInitResponse response = reply.getWorkerInitResponse();
                assertEquals(first, response.getHostConfiguration());
                assertEquals(StatusResult.Status.Success, response.getResult().getStatus());
                assertEquals(baseline, response.toBuilder().clearHostConfiguration().build());
            }
            stream.onNext(request("disabled-after-capture", false));
            assertFalse(take(replies).getWorkerInitResponse().hasHostConfiguration());
            assertEquals(1, lookups.get());
            assertEquals(1, opens.get());
            assertNull(streamFailure.get());
        } finally {
            server.shutdownNow().awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static StreamingMessage request(String id, boolean enabled) {
        WorkerInitRequest.Builder init = WorkerInitRequest.newBuilder()
                .setFunctionAppDirectory("not-the-local-root").setWorkerDirectory("worker-install");
        if (enabled) {
            init.putCapabilities("HostConfiguration", "1");
        }
        return StreamingMessage.newBuilder().setRequestId(id).setWorkerInitRequest(init).build();
    }

    private static StreamingMessage take(BlockingQueue<StreamingMessage> replies) throws InterruptedException {
        StreamingMessage reply = replies.poll(10, TimeUnit.SECONDS);
        assertNotNull(reply, "Worker init did not respond");
        return reply;
    }
}
