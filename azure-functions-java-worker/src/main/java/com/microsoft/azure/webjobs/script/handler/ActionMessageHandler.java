package com.microsoft.azure.webjobs.script.handler;

import java.util.*;

import com.google.protobuf.*;

public abstract class ActionMessageHandler<TReq extends Message> implements IMessageHandler {
    @SuppressWarnings("unchecked")
    public Optional<Message> execute(Message request) {
        this.executeCore((TReq) request);
        return Optional.empty();
    }

    protected abstract void executeCore(TReq request);
}
