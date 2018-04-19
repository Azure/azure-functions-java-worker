package com.microsoft.azure.webjobs.script.test.utilities;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.locks.*;
import java.util.function.*;
import java.util.stream.*;
import javax.annotation.*;
import javax.annotation.concurrent.*;

import com.google.protobuf.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;
import io.grpc.*;
import io.grpc.stub.*;
import org.apache.commons.lang3.tuple.*;

public final class FunctionsTestHost implements AutoCloseable, IApplication {
    public FunctionsTestHost() throws Exception {
        this.initializeServer();
        this.initializeClient();
    }

    @PostConstruct
    private void initializeServer() throws IOException {
        ServerBuilder<?> builder = ServerBuilder.forPort(this.getPort());
        this.grpcHost = new HostGrpcImplementation();
        this.server = builder.addService(this.grpcHost).build();
        this.server.start();
    }

    @PostConstruct
    private void initializeClient() throws InterruptedException {
        this.client = new JavaWorkerClient(this);
        this.client.listen("java-worker-test", HostGrpcImplementation.ESTABLISH_REQID);
        this.grpcHost.handleMessage(HostGrpcImplementation.ESTABLISH_REQID, m -> this.grpcHost.initWorker());
    }

    @Override
    public void close() throws Exception {
        this.client.close();
        this.server.shutdownNow().awaitTermination();
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
    public int getPort() { return 55005; }

    private JavaWorkerClient client;
    private HostGrpcImplementation grpcHost;
    private Server server;
    private String lastCallReqId = HostGrpcImplementation.LOADFUNC_REQID;


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

        void handleMessage(String requestId, Function<StreamingMessage, StreamingMessage> handler) throws InterruptedException {
            this.lock.lock();
            try {
                if (this.responder.get(requestId) == null) {
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
