package com.microsoft.azure.webjobs.script.handler;

import java.util.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

public class InvocationRequestHandler extends ReactiveMessageHandler<InvocationRequest, InvocationResponse> {
    public InvocationRequestHandler(JavaFunctionBroker broker) {
        this.broker = broker;
    }

    @Override
    public InvocationResponse.Builder executeCore(InvocationRequest request) {
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();

        try {
            List<ParameterBinding> outputBindings = this.broker.invokeMethod(functionId, request.getInputDataList());
            Application.LOGGER.info("Function \"" + functionId + "\" executed");

            StatusResult.Builder statusResult = StatusResult.newBuilder()
                    .setStatus(StatusResult.Status.Success)
                    .setResult("Function Executed, Return Value Here");
            return InvocationResponse.newBuilder()
                    .setInvocationId(invocationId)
                    .addAllOutputData(outputBindings)
                    .setResult(statusResult);
        } catch (Exception ex) {
            Application.LOGGER.warning("Function \"" + functionId + "\" cannot be invoked: " + Application.stackTraceToString(ex));
            return InvocationResponse.newBuilder().setInvocationId(invocationId).setResult(failedStatus(ex));
        }
    }

    private JavaFunctionBroker broker;
}
