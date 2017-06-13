package com.microsoft.azure.webjobs.script.handler;

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

        request.getTriggerMetadataMap().forEach((key, data) -> {
            System.out.println("-------------------------------------------------------");
            System.out.println("                    Trigger Metadata");
            System.out.println("-------------------------------------------------------");
            System.out.println("Key:       " + key);
            System.out.println("Data Type: " + data.getTypeVal());
            switch (data.getTypeVal()) {
                case String:
                    System.out.println("Data:      " + data.getStringVal());
                    break;
                case Bytes:
                    System.out.println("Data:      " + data.getBytesVal());
                    break;
                case Http:
                    System.out.println("Data:      " + data.getHttpVal());
                    break;
            }
            System.out.println("=======================================================");
        });

        try {
            ParameterBinding.Builder returnValue = this.broker.invokeMethod(functionId, request.getInputDataList());
            Application.LOGGER.info("Function \"" + functionId + "\" executed");

            StatusResult.Builder statusResult = StatusResult.newBuilder()
                    .setStatus(StatusResult.Status.Success)
                    .setResult("Function Executed, Return Value Here");
            return InvocationResponse.newBuilder()
                    .setInvocationId(invocationId)
                    .addOutputData(returnValue)
                    .setResult(statusResult);
        } catch (Exception ex) {
            Application.LOGGER.warning("Function \"" + functionId + "\" cannot be invoked: " + Application.stackTraceToString(ex));
            return InvocationResponse.newBuilder().setInvocationId(invocationId).setResult(failedStatus(ex));
        }
    }

    private JavaFunctionBroker broker;
}
