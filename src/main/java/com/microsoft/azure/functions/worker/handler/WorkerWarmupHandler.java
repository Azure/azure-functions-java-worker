package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.classloader.FactoryClassLoader;

import java.util.*;

import static com.microsoft.azure.functions.worker.Constants.JAVA_LIBRARY_DIRECTORY;


public class WorkerWarmupHandler extends MessageHandler<WorkerWarmupRequest, WorkerWarmupResponse.Builder> {

    private static final String WARM_UP_FUNCTION_NAME = "WarmupFunc";
    private static final String WARM_UP_FUNCTION_ENTRY_POINT = "com.microsoft.azure.functions.warmup.java.Function.run";
    private static final String WARM_UP_FUNCTION_SCRIPT_FILE = JAVA_LIBRARY_DIRECTORY + "/warmup-httptrigger.jar";
    private final JavaFunctionBroker javaFunctionBroker = new JavaFunctionBroker(new FactoryClassLoader().createClassLoaderProvider());

    public WorkerWarmupHandler() {
        super(StreamingMessage::getWorkerWarmupRequest,
                WorkerWarmupResponse::newBuilder,
                WorkerWarmupResponse.Builder::setResult,
                StreamingMessage.Builder::setWorkerWarmupResponse);
    }

    @Override
    String execute(WorkerWarmupRequest workerWarmupRequest, WorkerWarmupResponse.Builder builder) {
        try {
            WorkerLogManager.getSystemLogger().info("azure function java worker warm up start.");
            this.javaFunctionBroker.setWorkerDirectory(workerWarmupRequest.getWorkerDirectory());
            warmupFunctionEnvironmentReload();
            UUID functionId = warmupFunctionLoad(workerWarmupRequest);
            warmupInvocation(functionId);
            WorkerLogManager.getSystemLogger().info("azure function java worker warm up completed successfully.");
        } catch (Exception e) {
            WorkerLogManager.getSystemLogger().severe("warm up process failed with exception: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return "azure function java worker warm up completed";
    }

    private void warmupFunctionEnvironmentReload() throws Exception {
        FunctionEnvironmentReloadRequest functionEnvironmentReloadRequest = FunctionEnvironmentReloadRequest.newBuilder()
                .putAllEnvironmentVariables(System.getenv())
                .build();
        new FunctionEnvironmentReloadRequestHandler(this.javaFunctionBroker).execute(functionEnvironmentReloadRequest, null);
        WorkerLogManager.getSystemLogger().info("finish warm up FunctionEnvironmentReloadRequestHandler");
    }

    private UUID warmupFunctionLoad(WorkerWarmupRequest workerWarmupRequest) throws Exception {
        Map<String, BindingInfo> map = new HashMap<>();
        BindingInfo httpTrigger = BindingInfo.newBuilder().setDirection(BindingInfo.Direction.in).setDataType(BindingInfo.DataType.undefined).setType("httpTrigger").build();
        map.put("req", httpTrigger);
        BindingInfo http = BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).setDataType(BindingInfo.DataType.undefined).setType("http").build();
        map.put("$return", http);
        RpcFunctionMetadata rpcFunctionMetadata = RpcFunctionMetadata.newBuilder()
                .setName(WARM_UP_FUNCTION_NAME)
                .setEntryPoint(WARM_UP_FUNCTION_ENTRY_POINT)
                .setScriptFile(workerWarmupRequest.getWorkerDirectory() + WARM_UP_FUNCTION_SCRIPT_FILE)
                .putAllBindings(map)
                .build();
        final UUID functionId = UUID.randomUUID();
        FunctionLoadRequest functionLoadRequest = FunctionLoadRequest.newBuilder()
                .setFunctionId(functionId.toString())
                .setMetadata(rpcFunctionMetadata)
                .build();
        String loadRequestResult = new FunctionLoadRequestHandler(this.javaFunctionBroker, true).execute(functionLoadRequest, FunctionLoadResponse.newBuilder());
        WorkerLogManager.getSystemLogger().info("finish warm up FunctionLoadRequestHandler with result: " + loadRequestResult);
        return functionId;
    }

    private void warmupInvocation(UUID functionId) throws Exception {
        List<ParameterBinding> inputDataList = new ArrayList<>();
        ParameterBinding parameterBinding = ParameterBinding.newBuilder()
                .setName("req")
                .setData(TypedData.newBuilder().setHttp(RpcHttp.newBuilder().setMethod("GET")))
                .build();
        inputDataList.add(parameterBinding);
        InvocationRequest invocationRequest = InvocationRequest.newBuilder()
                .setFunctionId(functionId.toString())
                .setInvocationId(UUID.randomUUID().toString())
                .addAllInputData(inputDataList)
                .build();
        String invocationResult = new InvocationRequestHandler(this.javaFunctionBroker).execute(invocationRequest, InvocationResponse.newBuilder());
        WorkerLogManager.getSystemLogger().info("finish warm up InvocationRequestHandler with result: " + invocationResult);
    }
}
