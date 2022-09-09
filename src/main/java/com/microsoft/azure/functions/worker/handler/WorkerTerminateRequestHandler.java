package com.microsoft.azure.functions.worker.handler;

import com.google.protobuf.Message;
import com.microsoft.azure.functions.rpc.messages.StreamingMessage;
import com.microsoft.azure.functions.rpc.messages.WorkerTerminate;
import com.microsoft.azure.functions.worker.JavaWorkerClient;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.logging.Level;

public class WorkerTerminateRequestHandler extends MessageHandler<WorkerTerminate, Message.Builder> {

    public WorkerTerminateRequestHandler() {
        super(StreamingMessage::getWorkerTerminate,
                () -> null,
                null,
                null);
    }

    @Override
    String execute(WorkerTerminate workerTerminate, Message.Builder builder) {
        WorkerLogManager.getSystemLogger().log(Level.INFO, "Worker terminate request received. Gracefully shutting down the worker.");
        // Flushing the logs. Keeping the grpc client connection open here in case it is used by user's shutdown hooks
        WorkerLogManager.flushLogs();
        System.exit(0);
        return null;
    }

}
