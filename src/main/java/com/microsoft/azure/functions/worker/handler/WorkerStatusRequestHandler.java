package com.microsoft.azure.functions.worker.handler;
import com.microsoft.azure.functions.rpc.messages.*;

public class WorkerStatusRequestHandler extends MessageHandler<WorkerStatusRequest, WorkerStatusResponse.Builder> {

    public WorkerStatusRequestHandler() {
        super(StreamingMessage::getWorkerStatusRequest,
              WorkerStatusResponse::newBuilder,
              null,
              StreamingMessage.Builder::setWorkerStatusResponse);
    }

    @Override
    String execute(WorkerStatusRequest request, WorkerStatusResponse.Builder response) {
        return String.format("WorkerStatusRequest completed");
    }
}
