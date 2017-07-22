package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javax.annotation.*;

import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.handler.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

class JavaWorkerClient implements Closeable {
    JavaWorkerClient(Application app) {
        this.channel = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort()).usePlaintext(true).build();
        this.addHandlers();
    }

    @PostConstruct
    private void addHandlers() {
        JavaFunctionBroker broker = new JavaFunctionBroker();
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_INIT_REQUEST, WorkerInitRequestHandler::new);
        this.handlerSuppliers.put(StreamingMessage.ContentCase.FUNCTION_LOAD_REQUEST, () -> new FunctionLoadRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.INVOCATION_REQUEST, () -> new InvocationRequestHandler(broker));
    }

    void listen(String requestId) throws InterruptedException {
        new StreamingMessagePeer(requestId).await();
    }

    @Override
    public void close() throws IOException {
        this.channel.shutdown();
        try {
            this.channel.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    private class StreamingMessagePeer implements StreamObserver<StreamingMessage> {
        StreamingMessagePeer(String requestId) {
            this.send(requestId, new StartStreamHandler());
        }

        void await() throws InterruptedException {
            this.latch.await();
            if (this.error != null) {
                throw new RuntimeException(this.error);
            }
        }

        @Override
        public void onNext(StreamingMessage message) {
            MessageHandler<?, ?> handler = JavaWorkerClient.this.handlerSuppliers.get(message.getContentCase()).get();
            this.send(message.getRequestId(), handler.setRequest(message).handle());
        }

        @Override
        public void onCompleted() { this.latch.countDown(); }

        @Override
        public void onError(Throwable t) { this.error = t; this.onCompleted(); }

        private void send(String requestId, MessageHandler<?, ?> marshaller) {
            StreamingMessage.Builder messageBuilder = StreamingMessage.newBuilder().setRequestId(requestId);
            this.observer.onNext(marshaller.marshalResponse(messageBuilder).build());
        }

        private Throwable error = null;
        private CountDownLatch latch = new CountDownLatch(1);
        private StreamObserver<StreamingMessage> observer = FunctionRpcGrpc.newStub(JavaWorkerClient.this.channel).eventStream(this);
    }

    private ManagedChannel channel;
    private HashMap<StreamingMessage.ContentCase, Supplier<MessageHandler<?, ?>>> handlerSuppliers = new HashMap<>();
}
