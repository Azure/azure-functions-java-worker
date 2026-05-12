package com.microsoft.azure.functions.worker;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.logging.*;
import javax.annotation.PostConstruct;

import io.grpc.*;
import io.grpc.stub.*;

import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.handler.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Grpc client talks with the Azure Functions Runtime Host. It will dispatch to different message handlers according to the inbound message type.
 * Thread-Safety: Single thread.
 */
public class JavaWorkerClient implements AutoCloseable {
    private static final Logger logger = Logger.getLogger(JavaWorkerClient.class.getName());
    
    public JavaWorkerClient(IApplication app) {
        WorkerLogManager.initialize(this, app.logToConsole());
        ManagedChannelBuilder<?> chanBuilder = ManagedChannelBuilder.forAddress(app.getHost(), app.getPort());
        if (useTransportSecurity(app.getFunctionsUri())) {
            chanBuilder.useTransportSecurity();
        } else {
            chanBuilder.usePlaintext();
        }
        chanBuilder.maxInboundMessageSize(Integer.MAX_VALUE);

        this.channel = chanBuilder.build();
        this.peer = new AtomicReference<>(null);
        this.handlerSuppliers = new HashMap<>();
        this.classPathProvider = new FactoryClassLoader().createClassLoaderProvider();
        
        this.addHandlers();
    }

    @PostConstruct
    private void addHandlers() {
        JavaFunctionBroker broker = new JavaFunctionBroker(classPathProvider);
        
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_INIT_REQUEST, () -> new WorkerInitRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_WARMUP_REQUEST, WorkerWarmupHandler::new);
        this.handlerSuppliers.put(StreamingMessage.ContentCase.FUNCTION_ENVIRONMENT_RELOAD_REQUEST, () -> new FunctionEnvironmentReloadRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.FUNCTION_LOAD_REQUEST, () -> new FunctionLoadRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.INVOCATION_REQUEST, () -> new InvocationRequestHandler(broker));
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_STATUS_REQUEST, WorkerStatusRequestHandler::new);
        this.handlerSuppliers.put(StreamingMessage.ContentCase.WORKER_TERMINATE, WorkerTerminateRequestHandler::new);
    }

    public Future<Void> listen(String workerId, String requestId) {
        this.peer.set(new StreamingMessagePeer());
        this.peer.get().send(requestId, new StartStreamHandler(workerId));
        return this.peer.get().getListeningTask();
    }

    void logToHost(LogRecord record, String invocationId) {
        StreamingMessagePeer peer = this.peer.get();
        if (peer != null) {
            peer.send(null, new RpcLogHandler(record, invocationId));
        }
    }

    @Override
    public void close() throws Exception {
        this.peer.get().close();
        this.peer.set(null);
        this.channel.shutdownNow();
        this.channel.awaitTermination(15, TimeUnit.SECONDS);
        WorkerLogManager.deinitialize();
    }

    private class StreamingMessagePeer implements StreamObserver<StreamingMessage>, AutoCloseable {
        StreamingMessagePeer() {
            this.task = new CompletableFuture<>();
            this.threadpool = Executors.newCachedThreadPool();
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
            this.threadpool.submit(() -> {
                handler.handle();
                this.send(message.getRequestId(), handler);
            });
        }

        @Override
        public void onCompleted() { this.task.complete(null); }

        @Override
        public void onError(Throwable t) {
            // Extract gRPC status information from the throwable
            Status status = Status.fromThrowable(t);
            String statusCode = status.getCode().name();
            String statusDescription = status.getDescription();
            
            // Fallback to exception message if description is not available
            if (statusDescription == null) {
                statusDescription = t.getMessage() != null ? t.getMessage() : "No error description available";
            }
            
            logger.log(Level.SEVERE, String.format(
                "gRPC stream error. StatusCode: %s, Description: %s",
                statusCode, statusDescription), t);
            
            this.task.completeExceptionally(t);
        }

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
    private final ClassLoaderProvider classPathProvider;

    static boolean useTransportSecurity(String functionsUri) {
        if (functionsUri == null) {
            return false;
        }

        String scheme = parseFunctionsUriScheme(functionsUri);
        switch (scheme.toLowerCase(Locale.ROOT)) {
            case "http":
                return false;
            case "https":
                return true;
            default:
                throw new IllegalArgumentException(String.format(
                    "Unsupported functions URI scheme \"%s\" in functions URI \"%s\". Only http and https are supported.",
                    scheme, functionsUri));
        }
    }

    private static String parseFunctionsUriScheme(String functionsUri) {
        try {
            String scheme = new URI(functionsUri).getScheme();
            if (scheme == null || scheme.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                    "Unsupported functions URI \"%s\". Only http and https are supported.", functionsUri));
            }
            return scheme;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(String.format(
                "Error parsing functions URI \"%s\". Please provide a valid http or https URI.", functionsUri), ex);
        }
    }
}
