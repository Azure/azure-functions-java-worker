package com.microsoft.azure.webjobs.script.handler;

import java.util.*;

import com.google.protobuf.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public abstract class ReactiveMessageHandler<TReq extends Message, TRes extends Message> implements IMessageHandler {
    @SuppressWarnings("unchecked")
    public Optional<Message> execute(Message request) {
        return Optional.of(this.executeCore((TReq) request).build());
    }

    protected abstract TRes.Builder executeCore(TReq request);

    protected static StatusResult.Builder succeededStatus(String message) {
        return generateStatusResult(StatusResult.Status.Success, message);
    }

    protected static StatusResult.Builder failedStatus(Exception ex) {
        return generateStatusResult(StatusResult.Status.Failure, ex.getMessage());
    }

    private static StatusResult.Builder generateStatusResult(StatusResult.Status status, String message) {
        return StatusResult.newBuilder().setStatus(status).setResult(message);
    }
}
