package com.microsoft.azure.webjobs.script;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.*;

import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.handler.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * Grpc client talks with the Azure Functions Runtime Host. It will dispatch to different message handlers according to the inbound message type.
 * Thread-Safety: Single thread.
 */
class JavaWorkerClient implements AutoCloseable {
    JavaWorkerClient(Application app) {
        this.channel = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort()).usePlaintext(true).build();
        this.handlerSuppliers = new HashMap<>();
        this.addHandlers();
    }

    @PostConstruct
    private void addHandlers() {
        JavaFunctionBroker broker = new JavaFunctionBroker();
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_INIT_REQUEST, WorkerInitRequestHandler::new);
        this.handlerSuppliers.put(StreamingMessage.ContentCase.FUNCTION_LOAD_REQUEST, () -> new FunctionLoadRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.INVOCATION_REQUEST, () -> new InvocationRequestHandler(broker));
    }

    void listen(String workerId, String requestId) throws Exception {
        try (StreamingMessagePeer peer = new StreamingMessagePeer(workerId, requestId)) {
            peer.getListeningTask().get();
        }
    }

    @Override
    public void close() throws Exception {
        HostLoggingListener.releaseInstance();
        this.channel.shutdownNow();
        this.channel.awaitTermination(15, TimeUnit.SECONDS);
    }

    private class StreamingMessagePeer implements StreamObserver<StreamingMessage>, AutoCloseable {
        StreamingMessagePeer(String workerId, String requestId) {
            this.task = new CompletableFuture<>();
            this.threadpool = Executors.newWorkStealingPool();
            this.observer = FunctionRpcGrpc.newStub(JavaWorkerClient.this.channel).eventStream(this);
            this.send(requestId, new StartStreamHandler(workerId));
            HostLoggingListener.newInstance(this);
        }

        @Override
        public void close() throws Exception {
            this.threadpool.shutdown();
            this.threadpool.awaitTermination(15, TimeUnit.SECONDS);
            HostLoggingListener.releaseInstance();
            this.observer.onCompleted();
        }

        void log(LogRecord record, String invocationId) {
            this.send(null, new RpcLogHandler(record, invocationId));
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

    private ManagedChannel channel;
    private Map<StreamingMessage.ContentCase, Supplier<MessageHandler<?, ?>>> handlerSuppliers;
}
