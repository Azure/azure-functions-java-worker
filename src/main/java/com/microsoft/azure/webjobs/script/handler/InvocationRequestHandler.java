package com.microsoft.azure.webjobs.script.handler;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.broker.*;

public class InvocationRequestHandler extends ReactiveMessageHandler<InvocationRequest, InvocationResponse> {
    public InvocationRequestHandler(JavaFunctionBroker broker) {
        this.broker = broker;
    }

    @Override
    public InvocationResponse.Builder executeCore(InvocationRequest request) {
        final String functionId = request.getFunctionId();
        final String invocationId = request.getInvocationId();
        System.out.println("Trigger Metadata Map: ");
        request.getTriggerMetadataMap().forEach((k, v) -> System.out.println(k + ": " + v));
        System.out.println("Input Data List: ");
        request.getInputDataList().forEach((param) -> System.out.println(param.getName() + ": " + param.getData().getStringVal()));
        try {
            this.broker.invokeMethod(functionId);
            Application.LOGGER.info("Function \"" + functionId + "\" executed");

            StatusResult.Builder statusResult = StatusResult.newBuilder()
                    .setStatus(StatusResult.Status.Success)
                    .setResult("Function Executed, Return Value Here");

            ParameterBinding.Builder returnValue = ParameterBinding.newBuilder().
                    setName("$return").
                    setData(TypedData.newBuilder().setTypeVal(TypedData.Type.String).setStringVal("Test Return"));

            return InvocationResponse.newBuilder()
                    .setInvocationId(invocationId)
                    .addOutputData(returnValue)
                    .setResult(statusResult);
        } catch (Exception ex) {
            Application.LOGGER.warning("Function \"" + functionId + "\" cannot be invoked: " + ex);
            return InvocationResponse.newBuilder().setInvocationId(invocationId).setResult(failedStatus(ex));
        }
    }

    private JavaFunctionBroker broker;
}
