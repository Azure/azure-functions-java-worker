package com.microsoft.azure.webjobs.script.broker;

import java.net.*;
import java.util.*;
import java.util.logging.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * A broker between JAR methods and the function RPC. It can load methods using reflection, and invoke them at runtime.
 */
public class JavaFunctionBroker {
    public JavaFunctionBroker() {}

    public void loadMethod(String id, String jarPath, String methodName)
            throws ClassNotFoundException, MalformedURLException, IllegalAccessException {
        if (id == null || jarPath == null || methodName == null) {
            throw new NullPointerException("id, jarPath, methodName should not be null");
        }
        this.methods.put(id, new JavaMethodExecutor(jarPath, methodName));
    }

    public List<ParameterBinding> invokeMethod(String id, String invocationId, List<ParameterBinding> parameters) throws Exception {
        JavaMethodExecutor executor = this.methods.get(id);
        if (executor == null) { throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\""); }

        // TODO: Consider trigger metadata here
        InputDataStore inputs = new InputDataStore(parameters);
        inputs.addSource(this.createExecutionContext(invocationId));
        OutputDataStore outputs = new OutputDataStore();
        executor.execute(inputs, outputs);
        return outputs.toParameterBindings();
    }

    private ExecutionContext createExecutionContext(String invocationId) {
        Logger executionLogger = Logger.getAnonymousLogger();
        return new ExecutionContext.Builder()
                .setInvocationId(invocationId)
                .setLogger(executionLogger)
                .build();
    }

    private Map<String, JavaMethodExecutor> methods = new HashMap<>();
}
