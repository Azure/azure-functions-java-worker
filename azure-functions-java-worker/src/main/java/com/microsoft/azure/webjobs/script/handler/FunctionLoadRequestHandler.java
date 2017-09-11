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
        this.broker.loadMethod(functionId, script, entryPoint);
        return functionId + " - \"" + script + "\"::\"" + entryPoint + "\" loaded";
    }

    private final JavaFunctionBroker broker;
}
