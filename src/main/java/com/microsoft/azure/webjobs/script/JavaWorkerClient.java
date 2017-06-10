package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import com.google.protobuf.*;
import com.microsoft.azure.webjobs.script.broker.JavaFunctionBroker;
import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.webjobs.script.rpc.messages.*;
import com.microsoft.azure.webjobs.script.handler.*;

import javax.annotation.PostConstruct;

public class JavaWorkerClient implements Closeable, StreamObserver<StreamingMessage> {
    public JavaWorkerClient(Application app) {
        this.listenerLatch = null;
        this.channel = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort()).usePlaintext(true).build();
        this.functionStub = FunctionRpcGrpc.newStub(this.channel);
        this.requestObserver = null;
        this.addHandlers();
    }

    public void establishCommunication(String requestId) throws InterruptedException {
        if (this.listenerLatch == null) {
            this.listenerLatch = new CountDownLatch(1);
            this.listenerError = null;

            this.requestObserver = this.functionStub.eventStream(this);
            this.send(StartStream.newBuilder().build(), requestId);

            this.listenerLatch.await();
            this.listenerLatch = null;
            if (this.listenerError != null) {
                throw (this.listenerError instanceof RuntimeException)
                        ? (RuntimeException) this.listenerError
                        : new RuntimeException(this.listenerError);
            }
        } else {
            Application.LOGGER.severe("Trying to establish duplicated communication");
        }
    }

    @Override
    public void close() throws IOException {
        if (this.requestObserver != null) {
            this.requestObserver.onCompleted();
            this.requestObserver = null;
        }
        this.channel.shutdown();
        try {
            this.channel.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onNext(StreamingMessage message) {
        if (this.listenerLatch != null) {
            if (message != null) {
                IMessageHandler handler = this.handlers.getOrDefault(message.getType(), null);
                if (handler != null) {
                    Class<?> contentClass = INBOUND_TYPE_MAPPING.getOrDefault(message.getType(), null);
                    if (contentClass != null) {
                        try {
                            Message content = message.getContent().unpack((Class<Message>) contentClass);
                            handler.execute(content).ifPresent((c) -> this.send(c, message.getRequestId()));
                        } catch (InvalidProtocolBufferException ex) {
                            Application.LOGGER.severe("Unable to unpack content: " + ex);
                        } catch (ClassCastException ex) {
                            Application.LOGGER.severe("Unable to cast to Class<Message>: " + ex);
                        }
                    } else {
                        Application.LOGGER.severe("Content class not registered for \"" + message.getType() + "\"");
                    }
                } else {
                    Application.LOGGER.severe("No handlers for \"" + message.getType() + "\"");
                }
            } else {
                Application.LOGGER.warning("Received null StreamingMessage");
            }
        } else {
            Application.LOGGER.severe("Received message without listening");
        }
    }

    @Override
    public void onError(Throwable t) {
        if (this.listenerLatch != null) {
            if (t != null) {
                this.listenerError = t;
                this.listenerLatch.countDown();
            }
        } else {
            Application.LOGGER.severe("Received message without listening");
        }
    }

    @Override
    public void onCompleted() {
        if (this.listenerLatch != null) {
            this.listenerLatch.countDown();
        } else {
            Application.LOGGER.severe("Received message without listening");
        }
    }

    private void send(Message content, String requestId) {
        if (this.requestObserver != null) {
            StreamingMessage.Type contentType = OUTBOUND_TYPE_MAPPING.getOrDefault(content.getClass(), null);
            if (contentType != null) {
                StreamingMessage message = StreamingMessage.newBuilder()
                        .setType(contentType)
                        .setContent(Any.pack(content))
                        .setRequestId(requestId)
                        .build();
                this.requestObserver.onNext(message);
            } else {
                Application.LOGGER.severe("Message class \"" + content.getClass() + "\" is not supported");
            }
        } else {
            Application.LOGGER.severe("requestObserver is null");
        }
    }

    @PostConstruct
    private void addHandlers() {
        JavaFunctionBroker broker = new JavaFunctionBroker();

        this.handlers = new HashMap<>();
        this.handlers.put(StreamingMessage.Type.WorkerInitRequest, new WorkerInitRequestHandler());
        this.handlers.put(StreamingMessage.Type.FunctionLoadRequest, new FunctionLoadRequestHandler(broker));
        this.handlers.put(StreamingMessage.Type.InvocationRequest, new InvocationRequestHandler(broker));
    }

    private CountDownLatch listenerLatch;
    private Throwable listenerError;
    private ManagedChannel channel;
    private FunctionRpcGrpc.FunctionRpcStub functionStub;
    private StreamObserver<StreamingMessage> requestObserver;
    private HashMap<StreamingMessage.Type, IMessageHandler> handlers;

    private static final HashMap<Class<?>, StreamingMessage.Type> OUTBOUND_TYPE_MAPPING =
            new HashMap<Class<?>, StreamingMessage.Type>() {{
                put(StartStream.class, StreamingMessage.Type.StartStream);
                put(FunctionLoadResponse.class, StreamingMessage.Type.FunctionLoadResponse);
                put(InvocationResponse.class, StreamingMessage.Type.InvocationResponse);
            }};
    private static final HashMap<StreamingMessage.Type, Class<?>> INBOUND_TYPE_MAPPING =
            new HashMap<StreamingMessage.Type, Class<?>>() {{
                put(StreamingMessage.Type.FunctionLoadRequest, FunctionLoadRequest.class);
                put(StreamingMessage.Type.InvocationRequest, InvocationRequest.class);
            }};
}
