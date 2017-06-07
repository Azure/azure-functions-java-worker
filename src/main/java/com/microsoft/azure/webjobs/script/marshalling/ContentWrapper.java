package com.microsoft.azure.webjobs.script.marshalling;

import com.google.protobuf.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * ContentWrapper wraps the content of a StreamingMessage (like StartStream). We introduce this class to enable the
 * polymorphic behaviors of different types of content of StreamingMessage.
 */
public abstract class ContentWrapper {
    protected ContentWrapper(StreamingMessage.Type contentType, Message content) {
        this.type = contentType;
        this.content = content;
    }

    public StreamingMessage.Type getContentType() { return this.type; }
    public Message getContent() { return this.content; }

    private StreamingMessage.Type type;
    private Message content;
}
