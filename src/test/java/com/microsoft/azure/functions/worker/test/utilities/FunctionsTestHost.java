package com.microsoft.azure.functions.worker.test.utilities;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.*;
import javax.annotation.concurrent.*;

import com.google.protobuf.*;
import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.*;
import io.grpc.stub.*;
import org.apache.commons.lang3.tuple.*;

public final class FunctionsTestHost implements AutoCloseable, IApplication {
    public enum ClientTransport {
        LEGACY,
        HTTP,
        HTTPS
    }

    public enum ServerTransport {
        PLAINTEXT,
        TLS
    }

    private static final int RESPONSE_TIMEOUT_SECONDS = 10;
    private static final long RESPONSE_POLL_MILLIS = 100L;
    private static final String TLS_RESOURCE_ROOT = "grpc-tls/";
    private static final String TLS_CERTIFICATE_RESOURCE = TLS_RESOURCE_ROOT + "localhost-cert.pem";
    private static final String TLS_PRIVATE_KEY_RESOURCE = TLS_RESOURCE_ROOT + "localhost-key.pem";

    private int port;
    public FunctionsTestHost() throws Exception {
        this(ServerTransport.PLAINTEXT, ClientTransport.LEGACY);
    }

    public FunctionsTestHost(ServerTransport serverTransport, ClientTransport clientTransport) throws Exception {
        this.serverTransport = serverTransport;
        this.clientTransport = clientTransport;
        this.port = populatePort();
        try {
            this.initializeServer();
            this.initializeClient();
        } catch (Exception ex) {
            this.closeQuietly();
            throw ex;
        }
    }

    private final List<Integer> list = Arrays.asList(55005, 5005);

    private int populatePort() {
        try (ServerSocket ignored = new ServerSocket(this.list.get(0))) {
            return this.list.get(0);
        } catch (IOException e) {
            return this.list.get(1);
        }
    }

    @PostConstruct
    private void initializeServer() throws IOException {
        ServerBuilder<?> builder = this.serverTransport == ServerTransport.TLS
                ? NettyServerBuilder.forPort(this.getPort())
                    .sslContext(GrpcSslContexts.forServer(getTlsResource(TLS_CERTIFICATE_RESOURCE), getTlsResource(TLS_PRIVATE_KEY_RESOURCE)).build())
                : ServerBuilder.forPort(this.getPort());
        this.grpcHost = new HostGrpcImplementation();
        this.server = builder.addService(this.grpcHost).build();
        this.server.start();
    }

    @PostConstruct
    private void initializeClient() throws Exception {
        this.client = new JavaWorkerClient(this);
        this.listeningTask = this.client.listen("java-worker-test", HostGrpcImplementation.ESTABLISH_REQID);
        this.grpcHost.handleMessageWithTimeout(
                HostGrpcImplementation.ESTABLISH_REQID,
                m -> this.grpcHost.initWorker(),
                RESPONSE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        Exception closeException = null;
        if (this.client != null) {
            try {
                this.client.close();
            } catch (Exception ex) {
                closeException = ex;
            }
        }
        if (this.server != null) {
            this.server.shutdownNow().awaitTermination(15, TimeUnit.SECONDS);
        }
        if (closeException != null) {
            throw closeException;
        }
    }

    public void loadFunction(String id, String reflectionName, Map<String, BindingInfo> bindings) throws Exception {
        this.grpcHost.handleMessage(HostGrpcImplementation.INITWORKER_REQID, m -> this.grpcHost.loadFunction(id, reflectionName, bindings));
        this.grpcHost.handleMessage(HostGrpcImplementation.LOADFUNC_REQID, null);
    }

    @SafeVarargs
    public final InvocationResponse call(String reqId, String funcId, Triple<String, TypedData.DataCase, Object> ...params) throws Exception {
        AtomicReference<InvocationResponse> response = new AtomicReference<>();
        this.grpcHost.handleMessage(this.lastCallReqId, m -> this.grpcHost.invokeFunction(reqId, funcId, params));
        this.grpcHost.handleMessage(reqId, m -> {
            response.set(m.getInvocationResponse());
            return null;
        });
        this.lastCallReqId = reqId;
        return response.get();
    }

    @Override
    public boolean logToConsole() { return false; }
    @Override
    public String getHost() { return "localhost"; }
    @Override
    public int getPort() { return this.port; }
    @Override
    public String getFunctionsUri() {
        switch (this.clientTransport) {
            case HTTP:
                return "http://" + this.getHost() + ":" + this.getPort();
            case HTTPS:
                return "https://" + this.getHost() + ":" + this.getPort();
            default:
                return null;
        }
    }
    @Override
    public Integer getMaxMessageSize() { return null; }

    private JavaWorkerClient client;
    private HostGrpcImplementation grpcHost;
    private final ServerTransport serverTransport;
    private final ClientTransport clientTransport;
    private Server server;
    private Future<Void> listeningTask;
    private String lastCallReqId = HostGrpcImplementation.LOADFUNC_REQID;

    private void closeQuietly() {
        try {
            this.close();
        } catch (Exception ignored) {
        }
    }

    private static File getTlsResource(String resourcePath) {
        URL resource = FunctionsTestHost.class.getClassLoader().getResource(resourcePath);
        if (resource == null) {
            throw new IllegalStateException("Missing test TLS resource: " + resourcePath);
        }
        try {
            return Paths.get(resource.toURI()).toFile();
        } catch (URISyntaxException ex) {
            throw new IllegalStateException("Invalid test TLS resource path: " + resourcePath, ex);
        }
    }

    private void throwIfListeningFailed() throws ExecutionException, InterruptedException {
        if (this.listeningTask != null && this.listeningTask.isDone()) {
            this.listeningTask.get();
        }
    }


    @ThreadSafe
    private class HostGrpcImplementation extends FunctionRpcGrpc.FunctionRpcImplBase {
        static final String ESTABLISH_REQID = "establish", INITWORKER_REQID = "init-worker", LOADFUNC_REQID = "load-function";

        private final Lock lock = new ReentrantLock();
        private final Map<String, Condition> respReady = new HashMap<>();
        private final Map<String, StreamingMessage> respValue = new HashMap<>();
        private final Map<String, StreamObserver<StreamingMessage>> responder = new HashMap<>();

        private Condition getResponseCondition(String requestId) {
            return this.respReady.computeIfAbsent(requestId, k -> this.lock.newCondition());
        }

        private void setResponse(String requestId, StreamingMessage value, StreamObserver<StreamingMessage> client) {
            this.respValue.put(requestId, value);
            this.responder.put(requestId, client);
            this.getResponseCondition(requestId).signal();
        }

        void handleMessage(String requestId, Function<StreamingMessage, StreamingMessage> handler) throws Exception {
            this.lock.lock();
            try {
                while (this.responder.get(requestId) == null) {
                    this.getResponseCondition(requestId).await();
                }
                StreamingMessage message = this.respValue.get(requestId);
                StreamingMessage response = null;
                if (handler != null) {
                    response = handler.apply(message);
                }
                if (response != null) {
                    this.responder.get(requestId).onNext(response);
                }
            } finally {
                this.lock.unlock();
            }
        }

        void handleMessageWithTimeout(String requestId, Function<StreamingMessage, StreamingMessage> handler,
                                      long timeout, TimeUnit unit) throws Exception {
            this.lock.lock();
            try {
                long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
                while (this.responder.get(requestId) == null) {
                    FunctionsTestHost.this.throwIfListeningFailed();
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    if (remainingNanos <= 0) {
                        throw new TimeoutException("Timed out waiting for gRPC request " + requestId);
                    }
                    long waitMillis = Math.max(1L, Math.min(TimeUnit.NANOSECONDS.toMillis(remainingNanos), RESPONSE_POLL_MILLIS));
                    this.getResponseCondition(requestId).await(waitMillis, TimeUnit.MILLISECONDS);
                }
                StreamingMessage message = this.respValue.get(requestId);
                StreamingMessage response = null;
                if (handler != null) {
                    response = handler.apply(message);
                }
                if (response != null) {
                    this.responder.get(requestId).onNext(response);
                }
            } finally {
                this.lock.unlock();
            }
        }

        private StreamingMessage initWorker() {
            WorkerInitRequest.Builder request = WorkerInitRequest.newBuilder().setHostVersion("2.0.0");
            return StreamingMessage.newBuilder().setRequestId(INITWORKER_REQID).setWorkerInitRequest(request).build();
        }

        private StreamingMessage loadFunction(String id, String reflectionName, Map<String, BindingInfo> bindings) {
            RpcFunctionMetadata.Builder metadata = RpcFunctionMetadata.newBuilder()
                    .setName(reflectionName.substring(reflectionName.lastIndexOf('.') + 1))
                    .setDirectory(".")
                    .setScriptFile(System.getProperty("testing-project-jar"))
                    .setEntryPoint(reflectionName)
                    .putAllBindings(bindings);
            FunctionLoadRequest.Builder request = FunctionLoadRequest.newBuilder()
                    .setFunctionId(id)
                    .setMetadata(metadata);
            return StreamingMessage.newBuilder().setRequestId(LOADFUNC_REQID).setFunctionLoadRequest(request).build();
        }

        private StreamingMessage invokeFunction(String reqId, String funcId, Triple<String, TypedData.DataCase, Object>[] params) {
            List<ParameterBinding> bindings = Arrays.stream(params).map(p -> {
                ParameterBinding.Builder binding = ParameterBinding.newBuilder();
                TypedData.Builder data = TypedData.newBuilder();
                if (p.getLeft() != null && !p.getLeft().isEmpty()) {
                    binding.setName(p.getLeft());
                }
                switch (p.getMiddle()) {
                    case STRING:
                        data.setString((String) p.getRight());
                        break;
                    case JSON:
                        data.setJson((String) p.getRight());
                        break;
                    case BYTES:
                        data.setBytes((ByteString) p.getRight());
                        break;
                    case HTTP:
                        data.setHttp((RpcHttp.Builder) p.getRight());
                        break;
                    case INT:
                        data.setInt((long)p.getRight());
                        break;
                    case DOUBLE:
                        data.setDouble((double)p.getRight());
                        break;
                    default:
                        throw new UnsupportedOperationException(p.toString());
                }
                return binding.setData(data).build();
            }).collect(Collectors.toList());
            InvocationRequest.Builder request = InvocationRequest.newBuilder()
                    .setInvocationId(reqId)
                    .setFunctionId(funcId)
                    .addAllInputData(bindings);
            return StreamingMessage.newBuilder().setRequestId(reqId).setInvocationRequest(request).build();
        }

        @Override
        public StreamObserver<StreamingMessage> eventStream(StreamObserver<StreamingMessage> responseObserver) {
            return new StreamObserver<StreamingMessage>() {
                @Override
                public void onNext(StreamingMessage msg) {
                    HostGrpcImplementation.this.lock.lock();
                    try {
                        if (!msg.getContentCase().equals(StreamingMessage.ContentCase.RPC_LOG)) {
                            HostGrpcImplementation.this.setResponse(msg.getRequestId(), msg, responseObserver);
                        }
                    } finally {
                        HostGrpcImplementation.this.lock.unlock();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    throw new RuntimeException(t);
                }

                @Override
                public void onCompleted() {}
            };
        }
    }
}
