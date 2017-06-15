package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * A broker between JAR methods and the function RPC. It can load methods by using reflection, and invoke them at
 * runtime.
 */
public class JavaFunctionBroker {
    public JavaFunctionBroker() {
        this.methods = new HashMap<>();
    }

    public void loadMethod(String id, String jarPath, String methodName)
            throws ClassNotFoundException, MalformedURLException, IllegalAccessException, InstantiationException {
        if (id == null || jarPath == null || methodName == null) {
            throw new NullPointerException("id, jarPath, methodName should not be null");
        }
        this.methods.put(id, new JavaMethodExecutor(jarPath, methodName));
    }

    public ParameterBinding.Builder invokeMethod(String id, List<ParameterBinding> parameters)
            throws InvocationTargetException, IllegalAccessException {
        if (id == null) {
            throw new NullPointerException("id should not be null");
        }
        JavaMethodExecutor executor = this.methods.getOrDefault(id, null);
        if (executor == null) {
            throw new NullPointerException("Cannot find method with ID \"" + id + "\"");
        }

        // TODO: Consider trigger metadata here
        JavaMethodInput[] inputs = parameters.stream().map(JavaMethodInput::new).toArray(JavaMethodInput[]::new);
        JavaMethodOutput output = executor.execute(inputs);
        return output.toParameterBinding();
    }

    private Map<String, JavaMethodExecutor> methods;
}
