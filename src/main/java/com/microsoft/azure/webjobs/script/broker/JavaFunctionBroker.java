package com.microsoft.azure.webjobs.script.broker;

import java.net.MalformedURLException;
import java.util.*;

/**
 * A broker between JAR methods and the function RPC. It can load methods by using reflection, and invoke them at
 * runtime.
 */
public class JavaFunctionBroker {
    public JavaFunctionBroker() {
        this.methods = new HashMap<>();
    }

    public void loadMethod(String id, String jarPath, String methodName)
            throws ClassNotFoundException, NoSuchMethodException, MalformedURLException {
        if (id == null || jarPath == null || methodName == null) {
            throw new NullPointerException("id, jarPath, methodName should not be null");
        }
        this.methods.put(id, new JavaMethodReflector(jarPath, methodName));
    }

    public void invokeMethod(String id) {
        if (id == null) {
            throw new NullPointerException("id should not be null");
        }
        JavaMethodReflector reflector = this.methods.getOrDefault(id, null);
        if (reflector == null) {
            throw new NullPointerException("Cannot find method with ID \"" + id + "\"");
        }
    }

    private Map<String, JavaMethodReflector> methods;
}
