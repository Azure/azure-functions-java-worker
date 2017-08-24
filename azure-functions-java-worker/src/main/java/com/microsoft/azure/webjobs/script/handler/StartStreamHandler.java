package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class StartStreamHandler extends OutboundMessageHandler<StartStream.Builder> {
    public StartStreamHandler(String workerId) {
        super(() -> generateStartStream(workerId), StreamingMessage.Builder::setStartStream);
    }

    private static StartStream.Builder generateStartStream(String workerId) {
        assert workerId != null;
        StartStream.Builder startStream = StartStream.newBuilder();
        startStream.setWorkerId(workerId);
        return startStream;
    }
}
