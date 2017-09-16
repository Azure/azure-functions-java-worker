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

    public void loadMethod(String id, String jarPath, String methodName, Map<String, BindingInfo> bindings)
            throws ClassNotFoundException, MalformedURLException, IllegalAccessException {
        if (id == null || jarPath == null || methodName == null) {
            throw new NullPointerException("id, jarPath, methodName should not be null");
        }
        this.methods.put(id, new JavaMethodExecutor(jarPath, methodName, bindings));
    }

    public Optional<TypedData> invokeMethod(String id, InvocationRequest request, List<ParameterBinding> outputs) throws Exception {
        JavaMethodExecutor executor = this.methods.get(id);
        if (executor == null) { throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\""); }

        BindingDataStore dataStore = new BindingDataStore();
        dataStore.setBindingDefinitions(executor.getBindingDefinitions());
        dataStore.addTriggerMetadataSource(request.getTriggerMetadataMap());
        dataStore.addParameterSources(request.getInputDataList());
        dataStore.addExecutionContextSource(request.getInvocationId());

        executor.execute(dataStore);
        outputs.addAll(dataStore.getOutputParameterBindings(true));
        return dataStore.getDataTargetTypedValue(BindingDataStore.RETURN_NAME);
    }

    private final Map<String, JavaMethodExecutor> methods;
}
