package com.microsoft.azure.functions.worker.handler;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.rpc.messages.*;

public class WorkerInitRequestHandler extends MessageHandler<WorkerInitRequest, WorkerInitResponse.Builder> {
    public WorkerInitRequestHandler() {
        super(StreamingMessage::getWorkerInitRequest,
              WorkerInitResponse::newBuilder,
              WorkerInitResponse.Builder::setResult,
              StreamingMessage.Builder::setWorkerInitResponse);
    }

    @Override
    String execute(WorkerInitRequest request, WorkerInitResponse.Builder response) {
        response.setWorkerVersion(Application.version());
        response.putCapabilities("TypedDataCollection", "TypedDataCollection");
        response.putCapabilities("WorkerStatus", "WorkerStatus");
        response.putCapabilities("RpcHttpBodyOnly", "RpcHttpBodyOnly");
        response.putCapabilities("RpcHttpTriggerMetadataRemoved", "RpcHttpTriggerMetadataRemoved");
        response.setWorkerMetadata(composeWorkerMetaData());
        return "Worker initialized";
    }

    private WorkerMetadata.Builder composeWorkerMetaData(){
        WorkerMetadata.Builder workerMetadataBuilder = WorkerMetadata.newBuilder();
        workerMetadataBuilder.setRuntimeName("java");
        workerMetadataBuilder.setRuntimeVersion(System.getProperty("java.version"));
        workerMetadataBuilder.setWorkerVersion(Application.version());
        workerMetadataBuilder.setWorkerBitness(System.getProperty("os.arch"));
        return workerMetadataBuilder;
    }
}
