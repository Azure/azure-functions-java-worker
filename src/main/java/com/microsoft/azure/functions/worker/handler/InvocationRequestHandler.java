package com.microsoft.azure.functions.worker.handler;

import java.util.*;
import java.util.logging.*;

import com.microsoft.azure.functions.worker.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.rpc.messages.*;

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
        response.setInvocationId(invocationId);
       
        List<ParameterBinding> outputBindings = new ArrayList<>();
        this.broker.invokeMethod(functionId, request, outputBindings).ifPresent(response::setReturnValue);
        response.addAllOutputData(outputBindings);

        return String.format("Function \"%s\" (Id: %s) invoked by Java Worker",
                this.broker.getMethodName(functionId).orElse("UNKNOWN"), invocationId);
    }

    private JavaFunctionBroker broker;
    private Logger invocationLogger;
}
