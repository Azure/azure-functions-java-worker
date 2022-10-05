package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.BindingInfo;
import com.microsoft.azure.functions.worker.binding.BindingDefinition;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This Class encapsulate data about functions defined by customer.
 * Each function defined by customer will be used to build one FunctionDefinition object accordingly.
 */
public class FunctionDefinition {
    private final Class<?> containingClass;
    private final MethodBindInfo candidate;
    private final Map<String, BindingDefinition> bindingDefinitions;

    public FunctionDefinition(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);

        this.candidate = getCandidate(descriptor, containingClass);

        this.bindingDefinitions = new HashMap<>();

        for (Map.Entry<String, BindingInfo> entry : bindingInfos.entrySet()) {
            this.bindingDefinitions.put(entry.getKey(), new BindingDefinition(entry.getKey(), entry.getValue()));
        }
    }

    private static Class<?> getContainingClass(String className, ClassLoaderProvider classLoaderProvider) throws ClassNotFoundException {
        ClassLoader classLoader = classLoaderProvider.createClassLoader();
        return Class.forName(className, false, classLoader);
    }

    private static MethodBindInfo getCandidate(FunctionMethodDescriptor descriptor, Class<?> containingClass) throws NoSuchMethodException {
        List<MethodBindInfo> candidates  = new ArrayList<>();
        for (Method method : containingClass.getMethods()) {
            if (method.getDeclaringClass().getName().equals(descriptor.getFullClassName()) && method.getName().equals(descriptor.getMethodName())) {
                candidates.add(new MethodBindInfo(method));
            }
        }

        if (candidates.isEmpty()) {
            throw new NoSuchMethodException("There are no methods named \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        if (candidates.size() > 1) {
            throw new UnsupportedOperationException("Found more than one function with method name \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        return candidates.get(0);
    }

    public Class<?> getContainingClass() {
        return containingClass;
    }

    public Map<String, BindingDefinition> getBindingDefinitions() {
        return bindingDefinitions;
    }

    public MethodBindInfo getCandidate() {
        return candidate;
    }
}
