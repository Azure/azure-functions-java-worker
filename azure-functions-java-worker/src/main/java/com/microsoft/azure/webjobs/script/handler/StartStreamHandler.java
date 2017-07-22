package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class StartStreamHandler extends OutboundMessageHandler<StartStream.Builder> {
    public StartStreamHandler() {
        super(StartStream::newBuilder, StreamingMessage.Builder::setStartStream);
    }
}
