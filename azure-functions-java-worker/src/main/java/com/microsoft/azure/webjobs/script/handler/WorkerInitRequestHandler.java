package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

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
        return "Worker initialized";
    }
}
