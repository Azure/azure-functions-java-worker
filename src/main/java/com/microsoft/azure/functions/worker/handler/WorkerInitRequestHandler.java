package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.JavaFunctionBroker;

public class WorkerInitRequestHandler extends MessageHandler<WorkerInitRequest, WorkerInitResponse.Builder> {
    public WorkerInitRequestHandler(JavaFunctionBroker broker) {
        super(StreamingMessage::getWorkerInitRequest,
              WorkerInitResponse::newBuilder,
              WorkerInitResponse.Builder::setResult,
              StreamingMessage.Builder::setWorkerInitResponse);
        this.broker = broker;
    }

    @Override
    String execute(WorkerInitRequest request, WorkerInitResponse.Builder response) {
        broker.setWorkerDirectory(request.getWorkerDirectory());
        response.setWorkerVersion(Application.version());
        response.putCapabilities("TypedDataCollection", "TypedDataCollection");
        response.putCapabilities("WorkerStatus", "WorkerStatus");
        response.putCapabilities("RpcHttpBodyOnly", "RpcHttpBodyOnly");
        response.putCapabilities("RpcHttpTriggerMetadataRemoved", "RpcHttpTriggerMetadataRemoved");
        response.setWorkerMetadata(composeWorkerMetadata());
        return "Worker initialized";
    }

    private WorkerMetadata.Builder composeWorkerMetadata(){
        WorkerMetadata.Builder workerMetadataBuilder = WorkerMetadata.newBuilder();
        workerMetadataBuilder.setRuntimeName("java");
        workerMetadataBuilder.setRuntimeVersion(System.getProperty("java.version"));
        workerMetadataBuilder.setWorkerVersion(Application.version());
        workerMetadataBuilder.setWorkerBitness(System.getProperty("os.arch"));
        return workerMetadataBuilder;
    }

    private final JavaFunctionBroker broker;
}
