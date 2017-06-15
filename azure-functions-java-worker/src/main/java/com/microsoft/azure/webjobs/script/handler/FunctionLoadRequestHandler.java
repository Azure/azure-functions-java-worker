package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.Application;
import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class FunctionLoadRequestHandler extends ReactiveMessageHandler<FunctionLoadRequest, FunctionLoadResponse> {
    public FunctionLoadRequestHandler(JavaFunctionBroker broker) {
        this.broker = broker;
    }

    @Override
    public FunctionLoadResponse.Builder executeCore(FunctionLoadRequest request) {
        final String functionId = request.getFunctionId();
        final RpcFunctionMetadata metadata = request.getMetadata();
        final String script = metadata.getScriptFile();
        final String entryPoint = metadata.getEntryPoint();
        try {
            this.broker.loadMethod(functionId, script, entryPoint);
            Application.LOGGER.info(functionId + " - \"" + script + "\"::\"" + entryPoint + "\" loaded");
            return FunctionLoadResponse.newBuilder()
                    .setFunctionId(functionId)
                    .setResult(succeededStatus("Function Loaded"));
        } catch (Exception ex) {
            Application.LOGGER.warning(
                    functionId + " - \"" + script + "\"::\"" + entryPoint + "\" cannot be loaded: " + ex);
            return FunctionLoadResponse.newBuilder().setResult(failedStatus(ex));
        }
    }

    private JavaFunctionBroker broker;
}
