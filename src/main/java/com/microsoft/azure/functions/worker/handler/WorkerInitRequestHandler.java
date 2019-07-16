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
        response.putCapabilities("TYPED_DATA_COLLECTION_SUPPORTED", "TRUE");
        return "Worker initialized";
    }
}
