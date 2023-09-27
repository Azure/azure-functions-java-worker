package com.microsoft.azure.functions.worker.invoker;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.exception.*;

/**
 * Used to run the actual method with specific arguments using reflection.
 * Thread-Safety: Single thread.
 */
public class MethodInvoker {
    private final Method method;
    private final List<Object> arguments;

    public MethodInvoker(Method method) {
        this.method = method;
        this.arguments = new ArrayList<>();
    }

    public void addArgument(Object argument) {
        this.arguments.add(argument);
    }

    public Object invoke(Object instance) throws Exception {
        instance = Modifier.isStatic(this.method.getModifiers()) ? null : instance;
        try {
            return this.method.invoke(instance, this.arguments.toArray());
        } catch (Exception ex) {
            return ExceptionUtils.rethrow(ex);
        }
    }
}
