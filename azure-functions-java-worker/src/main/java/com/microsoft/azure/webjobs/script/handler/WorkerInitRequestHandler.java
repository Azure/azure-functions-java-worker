package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class WorkerInitRequestHandler extends ActionMessageHandler<WorkerInitRequest> {
    @Override
    public void executeCore(WorkerInitRequest request) {
        Application.LOGGER.severe("Not implemented");
    }
}
