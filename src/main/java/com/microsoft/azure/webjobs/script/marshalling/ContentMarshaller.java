package com.microsoft.azure.webjobs.script.marshalling;

import java.util.*;

import com.google.protobuf.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * It will marshal messages of different kind to StreamingMessage. It will also unmarshal the StreamingMessage to
 * concrete messages.
 */
public class ContentMarshaller {
    public ContentMarshaller(String requestId) {
        this.requestId = requestId;
    }

    public StreamingMessage marshal(ContentWrapper content) {
        return StreamingMessage.newBuilder()
                .setType(content.getContentType())
                .setContent(Any.pack(content.getContent()))
                .setRequestId(this.requestId)
                .build();
    }

    public Optional<ContentWrapper> unmarshal(StreamingMessage message) {
        try {
            switch (message.getType()) {
                case StartStream:
                    return Optional.of(new StartStreamContent(message.getContent().unpack(StartStream.class)));
                default:
                    Application.LOGGER.warning("Message type \"" + message.getType() + "\" not recognized");
                    break;
            }
        } catch (InvalidProtocolBufferException ex) {
            Application.LOGGER.warning("Message content unpack error \"" + ex + "\"");
        }
        return Optional.empty();
    }

    private String requestId;
}
