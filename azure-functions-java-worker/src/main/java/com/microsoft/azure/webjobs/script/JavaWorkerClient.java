package com.microsoft.azure.webjobs.script;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;

import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.handler.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;
import com.microsoft.azure.webjobs.script.reflect.DefaultClassLoaderProvider;

/**
 * Grpc client talks with the Azure Functions Runtime Host. It will dispatch to different message handlers according to the inbound message type.
 * Thread-Safety: Single thread.
 */
class JavaWorkerClient implements AutoCloseable {
    JavaWorkerClient(Application app) {
        WorkerLogManager.initialize(this, app.logToConsole());
        this.channel = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort()).usePlaintext(true).build();
        this.peer = new AtomicReference<>(null);
        this.handlerSuppliers = new HashMap<>();
        this.classPathProvider = new DefaultClassLoaderProvider();
        
        this.addHandlers();
    }

    @PostConstruct
    private void addHandlers() {
        JavaFunctionBroker broker = new JavaFunctionBroker(classPathProvider);
        
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_INIT_REQUEST, WorkerInitRequestHandler::new);
        this.handlerSuppliers.put(StreamingMessage.ContentCase.FUNCTION_LOAD_REQUEST, () -> new FunctionLoadRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.INVOCATION_REQUEST, () -> new InvocationRequestHandler(broker));
    }

    void listen(String workerId, String requestId) throws Exception {
        try (StreamingMessagePeer peer = new StreamingMessagePeer()) {
            this.peer.set(peer);
            peer.send(requestId, new StartStreamHandler(workerId));
            peer.getListeningTask().get();
        } finally {
            this.peer.set(null);
        }
    }

    void logToHost(LogRecord record, String invocationId) {
        StreamingMessagePeer peer = this.peer.get();
        if (peer != null) {
            peer.send(null, new RpcLogHandler(record, invocationId));
        }
    }

    @Override
    public void close() throws Exception {
        this.channel.shutdownNow();
        this.channel.awaitTermination(15, TimeUnit.SECONDS);
    }

    private class StreamingMessagePeer implements StreamObserver<StreamingMessage>, AutoCloseable {
        StreamingMessagePeer() {
            this.task = new CompletableFuture<>();
            this.threadpool = Executors.newWorkStealingPool();
            this.observer = FunctionRpcGrpc.newStub(JavaWorkerClient.this.channel).eventStream(this);
        }

        @Override
        public synchronized void close() throws Exception {
            this.threadpool.shutdown();
            this.threadpool.awaitTermination(15, TimeUnit.SECONDS);
            this.observer.onCompleted();
        }

        /**
         * Handles the request. Grpc will not accept the next request until you exit this method.
         * @param message The incoming Grpc generic message.
         */
        @Override
        public void onNext(StreamingMessage message) {
            MessageHandler<?, ?> handler = JavaWorkerClient.this.handlerSuppliers.get(message.getContentCase()).get();
            handler.setRequest(message);
            handler.registerTask(this.threadpool.submit(() -> {
                handler.handle();
                this.send(message.getRequestId(), handler);
            }));
        }

        @Override
        public void onCompleted() { this.task.complete(null); }

        @Override
        public void onError(Throwable t) { this.task.completeExceptionally(t); }

        private CompletableFuture<Void> getListeningTask() { return this.task; }

        private synchronized void send(String requestId, MessageHandler<?, ?> marshaller) {
            StreamingMessage.Builder messageBuilder = StreamingMessage.newBuilder();
            if (requestId != null) { messageBuilder.setRequestId(requestId); }
            marshaller.marshalResponse(messageBuilder);
            this.observer.onNext(messageBuilder.build());
        }

        private CompletableFuture<Void> task;
        private ExecutorService threadpool;
        private StreamObserver<StreamingMessage> observer;
    }

    private final ManagedChannel channel;
    private final AtomicReference<StreamingMessagePeer> peer;
    private final Map<StreamingMessage.ContentCase, Supplier<MessageHandler<?, ?>>> handlerSuppliers;
    private final DefaultClassLoaderProvider classPathProvider;
}
