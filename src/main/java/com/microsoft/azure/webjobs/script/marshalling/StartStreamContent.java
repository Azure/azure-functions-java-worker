package com.microsoft.azure.webjobs.script.marshalling;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class StartStreamContent extends ContentWrapper {
    public StartStreamContent(StartStream content) {
        super(StreamingMessage.Type.StartStream, content);
    }

    public StartStreamContent(String workerId) {
        this(StartStream.newBuilder().setWorkerId(workerId).build());
    }
}
