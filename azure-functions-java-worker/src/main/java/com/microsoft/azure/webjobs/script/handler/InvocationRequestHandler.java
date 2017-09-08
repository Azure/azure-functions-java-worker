package com.microsoft.azure.webjobs.script.handler;

import java.util.*;
import java.util.logging.Logger;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class InvocationRequestHandler extends MessageHandler<InvocationRequest, InvocationResponse.Builder> {
    public InvocationRequestHandler(JavaFunctionBroker broker) {
        super(StreamingMessage::getInvocationRequest,
              InvocationResponse::newBuilder,
              InvocationResponse.Builder::setResult,
              StreamingMessage.Builder::setInvocationResponse);
        assert broker != null;
        this.broker = broker;
        this.invocationLogger = super.getLogger();
    }

    @Override
    Logger getLogger() { return this.invocationLogger; }

    @Override
    String execute(InvocationRequest request, InvocationResponse.Builder response) throws Exception {
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();


        this.invocationLogger = WorkerLogManager.getInvocationLogger(invocationId);
        List<ParameterBinding> outputBindings = this.broker.invokeMethod(functionId, invocationId, request.getInputDataList());

        // TODO: Simplify OutputData Stuffs (using the new binding information when loading a function)
        // TODO: Treat return value more elegant
        int retValueIndex = 0;
        for (; retValueIndex < outputBindings.size(); retValueIndex++)
            if (outputBindings.get(retValueIndex).getName().equals("$return"))
                break;
        if (retValueIndex < outputBindings.size()) {
            response.setReturnValue(outputBindings.get(retValueIndex).getData());
            outputBindings.remove(retValueIndex);
        }

        // TODO: Move this up when Host BUG is fixed
        response.setInvocationId(invocationId);
        response.addAllOutputData(outputBindings);
        return "Function \"" + functionId + "\" executed";
    }

    private JavaFunctionBroker broker;
    private Logger invocationLogger;
}
