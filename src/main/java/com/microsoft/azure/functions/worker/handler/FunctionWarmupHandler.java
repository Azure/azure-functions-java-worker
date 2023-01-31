package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.reflect.FactoryClassLoader;

import java.util.*;
import java.util.logging.Level;


public class FunctionWarmupHandler extends MessageHandler<FunctionWarmupRequest, FunctionWarmupResponse.Builder> {

    private static final String WARM_UP_FUNCTION_NAME = "WarmupFunc";
    private static final String WARM_UP_FUNCTION_ENTRY_POINT = "com.microsoft.azure.functions.warmup.java.Function.run";
    private static final String WARM_UP_FUNCTION_SCRIPT_FILE = "/annotationLib/warmup-httptrigger.jar";
    private final JavaFunctionBroker javaFunctionBroker = new JavaFunctionBroker(new FactoryClassLoader().createClassLoaderProvider());

    public FunctionWarmupHandler() {
        super(StreamingMessage::getFunctionWarmupRequest,
                FunctionWarmupResponse::newBuilder,
                FunctionWarmupResponse.Builder::setResult,
                StreamingMessage.Builder::setFunctionWarmupResponse);
    }

    @Override
    String execute(FunctionWarmupRequest functionWarmupRequest, FunctionWarmupResponse.Builder builder) {

        try {
            WorkerLogManager.getSystemLogger().info("azure function java worker warm up start.");
            this.javaFunctionBroker.setWorkerDirectory(functionWarmupRequest.getWorkerDirectory());
            warmupFunctionEnvironmentReloadRequestHandler(functionWarmupRequest);
            UUID functionId = warmupFunctionLoadRequestHandler(functionWarmupRequest);
            warmupInvocationRequestHandler(functionId);
            WorkerLogManager.getSystemLogger().info("warm up completed successfully.");
        } catch (Exception e) {
            WorkerLogManager.getSystemLogger().severe("warm up process failed with exception: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return "azure function java worker warm up completed";
    }

    private void warmupFunctionEnvironmentReloadRequestHandler(FunctionWarmupRequest functionWarmupRequest) throws Exception {
        FunctionEnvironmentReloadRequest.Builder functionEnvironmentReloadRequestBuilder = FunctionEnvironmentReloadRequest.newBuilder();
        FunctionEnvironmentReloadRequest functionEnvironmentReloadRequest = functionEnvironmentReloadRequestBuilder.putAllEnvironmentVariables(System.getenv()).build();
        new FunctionEnvironmentReloadRequestHandler(this.javaFunctionBroker).execute(functionEnvironmentReloadRequest, null);
        WorkerLogManager.getSystemLogger().info("finish warm up FunctionEnvironmentReloadRequestHandler");
    }

    private UUID warmupFunctionLoadRequestHandler(FunctionWarmupRequest functionWarmupRequest) throws Exception {
        FunctionLoadRequest.Builder functionLoadRequestBuilder = FunctionLoadRequest.newBuilder();
        RpcFunctionMetadata.Builder rpcFunctionMetadataBuilder = RpcFunctionMetadata.newBuilder();
        rpcFunctionMetadataBuilder.setName(WARM_UP_FUNCTION_NAME);
        rpcFunctionMetadataBuilder.setEntryPoint(WARM_UP_FUNCTION_ENTRY_POINT);
        rpcFunctionMetadataBuilder.setScriptFile(functionWarmupRequest.getWorkerDirectory() + WARM_UP_FUNCTION_SCRIPT_FILE);
        Map<String, BindingInfo> map = new HashMap<>();
        BindingInfo httpTrigger = BindingInfo.newBuilder().setDirection(BindingInfo.Direction.in).setDataType(BindingInfo.DataType.undefined).setType("httpTrigger").build();
        map.put("req", httpTrigger);
        BindingInfo http = BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).setDataType(BindingInfo.DataType.undefined).setType("http").build();
        map.put("$return", http);
        rpcFunctionMetadataBuilder.putAllBindings(map);
        final UUID functionId = UUID.randomUUID();
        functionLoadRequestBuilder.setFunctionId(functionId.toString());
        functionLoadRequestBuilder.setMetadata(rpcFunctionMetadataBuilder);
        String loadRequestResult = new FunctionLoadRequestHandler(this.javaFunctionBroker, true).execute(functionLoadRequestBuilder.build(), FunctionLoadResponse.newBuilder());
        WorkerLogManager.getSystemLogger().info("finish warm up FunctionLoadRequestHandler with result: " + loadRequestResult);
        return functionId;
    }

    private void warmupInvocationRequestHandler(UUID functionId) throws Exception {
        InvocationRequest.Builder invocationRequestBuilder = InvocationRequest.newBuilder();
        invocationRequestBuilder.setFunctionId(functionId.toString()).setInvocationId(UUID.randomUUID().toString());
        List<ParameterBinding> inputDataList = new ArrayList<>();
        ParameterBinding.Builder parameterBindingBuilder = ParameterBinding.newBuilder();
        parameterBindingBuilder.setName("req");
        parameterBindingBuilder.setData(TypedData.newBuilder().setHttp(RpcHttp.newBuilder().setMethod("GET")));
        inputDataList.add(parameterBindingBuilder.build());
        invocationRequestBuilder.addAllInputData(inputDataList);
        InvocationResponse.Builder invocationResponseBuilder = InvocationResponse.newBuilder();
        String invocationResult = new InvocationRequestHandler(this.javaFunctionBroker).execute(invocationRequestBuilder.build(), invocationResponseBuilder);
        WorkerLogManager.getSystemLogger().info("finish warm up InvocationRequestHandler with result: " + invocationResult);
    }
}
