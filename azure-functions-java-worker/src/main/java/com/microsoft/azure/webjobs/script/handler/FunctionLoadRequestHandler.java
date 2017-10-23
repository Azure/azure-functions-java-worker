package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class FunctionLoadRequestHandler extends MessageHandler<FunctionLoadRequest, FunctionLoadResponse.Builder> {
    public FunctionLoadRequestHandler(JavaFunctionBroker broker) {
        super(StreamingMessage::getFunctionLoadRequest,
              FunctionLoadResponse::newBuilder,
              FunctionLoadResponse.Builder::setResult,
              StreamingMessage.Builder::setFunctionLoadResponse);
        this.broker = broker;
    }

    @Override
    String execute(FunctionLoadRequest request, FunctionLoadResponse.Builder response) throws Exception {
        final String functionId = request.getFunctionId();
        response.setFunctionId(functionId);
        final String script = request.getMetadata().getScriptFile();
        final String entryPoint = request.getMetadata().getEntryPoint();
        final String functionName = request.getMetadata().getName();
        this.broker.loadMethod(functionId, functionName, script, entryPoint, request.getMetadata().getBindingsMap());

        return String.format("\"%s\" loaded (ID: %s, Reflection: \"%s\"::\"%s\")",
                functionName, functionId, script, entryPoint);
    }

    private final JavaFunctionBroker broker;
}
