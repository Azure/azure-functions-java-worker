package com.microsoft.azure.webjobs.script.handler;

import java.util.*;

import com.google.protobuf.*;

public interface IMessageHandler {
    Optional<Message> execute(Message request);
}
