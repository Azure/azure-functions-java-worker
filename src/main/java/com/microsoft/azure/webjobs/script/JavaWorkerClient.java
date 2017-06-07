package com.microsoft.azure.webjobs.script;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.webjobs.script.marshalling.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class JavaWorkerClient implements Closeable {
    public JavaWorkerClient(Application app) {
        this.channel = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort()).usePlaintext(true).build();
        this.functionStub = FunctionRpcGrpc.newStub(this.channel);
        this.marshaller = new ContentMarshaller(app.getRequestId());
    }

    public void establishCommunication() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final Vector<Throwable> responseErrors = new Vector<>();

        this.requestObserver = this.functionStub.eventStream(new StreamObserver<StreamingMessage>() {
            @Override
            public void onNext(StreamingMessage message) {
                JavaWorkerClient.this.marshaller.unmarshal(message).ifPresent(JavaWorkerClient.this::received);
            }

            @Override
            public void onError(Throwable t) {
                responseErrors.add(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() { latch.countDown(); }
        });

        this.send(new StartStreamContent("N/A"));

        latch.await();
        if (!responseErrors.isEmpty()) {
            throw (responseErrors.firstElement() instanceof RuntimeException)
                    ? (RuntimeException) responseErrors.firstElement()
                    : new RuntimeException(responseErrors.firstElement());
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

    public void send(ContentWrapper content) {
        if (requestObserver != null) {
            this.requestObserver.onNext(this.marshaller.marshal(content));
        } else {
            Application.LOGGER.severe("requestObserver is null");
        }
    }

    private void received(ContentWrapper content) {
        System.out.println("content received: " + content);
    }

    private ManagedChannel channel;
    private FunctionRpcGrpc.FunctionRpcStub functionStub;
    private StreamObserver<StreamingMessage> requestObserver = null;
    private ContentMarshaller marshaller;
}
