package com.microsoft.azure.webjobs.script.handler;

import java.util.*;

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
    }

    @Override
    String execute(InvocationRequest request, InvocationResponse.Builder response) throws Exception {
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();
        try {
            HostLoggingListener.getInstance().ifPresent(h -> h.pushInvocation(invocationId));
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

            response.setInvocationId(invocationId).addAllOutputData(outputBindings);
            return "Function \"" + functionId + "\" executed";
        } finally {
            HostLoggingListener.getInstance().ifPresent(HostLoggingListener::popInvocation);
        }
    }

    private JavaFunctionBroker broker;
}
