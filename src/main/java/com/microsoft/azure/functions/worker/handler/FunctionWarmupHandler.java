package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;
import com.microsoft.azure.functions.worker.reflect.EnhancedClassLoaderProvider;
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
    String execute(FunctionWarmupRequest functionWarmupRequest, FunctionWarmupResponse.Builder builder) throws Exception {

        //warm up FunctionEnvironmentReloadRequestHandler
        try {
            WorkerLogManager.getSystemLogger().info("warm up start. ");
            this.javaFunctionBroker.setWorkerDirectory(functionWarmupRequest.getWorkerDirectory());
            FunctionEnvironmentReloadRequest.Builder functionEnvironmentReloadRequestBuilder = FunctionEnvironmentReloadRequest.newBuilder();
            FunctionEnvironmentReloadRequest functionEnvironmentReloadRequest = functionEnvironmentReloadRequestBuilder.putAllEnvironmentVariables(System.getenv()).build();
            new FunctionEnvironmentReloadRequestHandler(this.javaFunctionBroker).execute(functionEnvironmentReloadRequest, null);

            //warm up FunctionLoadRequestHandler
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
            String loadRequestResult = new FunctionLoadRequestHandler(this.javaFunctionBroker).execute(functionLoadRequestBuilder.build(), FunctionLoadResponse.newBuilder());
            WorkerLogManager.getSystemLogger().log(Level.INFO, "warm up load request result: {0}", loadRequestResult);

            //warm up InvocationRequestHandler
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

            WorkerLogManager.getSystemLogger().log(Level.INFO, "warm up invocation result: {0}", invocationResult);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            throw new RuntimeException(e);
        }
        return "warm up completed";
    }
}
