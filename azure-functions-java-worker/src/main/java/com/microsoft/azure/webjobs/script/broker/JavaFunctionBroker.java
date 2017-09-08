package com.microsoft.azure.webjobs.script.broker;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * A broker between JAR methods and the function RPC. It can load methods using reflection, and invoke them at runtime.
 * Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {
    public JavaFunctionBroker() {
        this.methods = new ConcurrentHashMap<>();
    }

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
        inputs.addSource(new JavaExecutionContext(invocationId));
        OutputDataStore outputs = executor.execute(inputs);
        return outputs.toParameterBindings();
    }

    private final Map<String, JavaMethodExecutor> methods;
}

class JavaExecutionContext implements ExecutionContext {
    JavaExecutionContext(String invocationId) {
        assert invocationId != null && !invocationId.isEmpty();
        this.invocationId = invocationId;
        this.logger = WorkerLogManager.getInvocationLogger(invocationId);
    }

    public String getInvocationId() { return this.invocationId; }
    public Logger getLogger() { return this.logger; }

    private String invocationId;
    private Logger logger;
}
