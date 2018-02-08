package com.microsoft.azure.webjobs.script.handler;

import java.util.Map;

import com.microsoft.azure.webjobs.script.broker.*;
import com.microsoft.azure.webjobs.script.reflect.FunctionDescriptor;
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
        final RpcFunctionMetadata metadata = request.getMetadata();
        final FunctionDescriptor function = createFunctionDescriptor(request.getFunctionId(), metadata);
        
      
        final String methodName = metadata.getEntryPoint();
        final Map<String, BindingInfo> bindings = metadata.getBindingsMap();

        response.setFunctionId(function.getId());
        this.broker.loadMethod(function, methodName, bindings);

        return String.format("\"%s\" loaded (ID: %s, Reflection: \"%s\"::\"%s\")", function.getName(), function.getId(), function.getJarPath(), methodName);
    }
    
    FunctionDescriptor createFunctionDescriptor(String functionId, RpcFunctionMetadata metadata) {
		return new FunctionDescriptor(functionId, metadata.getName(), metadata.getScriptFile());  	
    }

    private final JavaFunctionBroker broker;
}
