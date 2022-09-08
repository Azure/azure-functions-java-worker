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

public class FunctionDefinition {
    private final Class<?> containingClass;
    private final Map<String, BindingDefinition> bindingDefinitions;

    public FunctionDefinition(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.candidates = new ArrayList<>();
        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);

        for (Method method : this.containingClass.getMethods()) {
            if (method.getDeclaringClass().getName().equals(descriptor.getFullClassName()) && method.getName().equals(descriptor.getMethodName())) {
                this.addCandidate(method);
            }
        }

        if (!hasCandidates()) {
            throw new NoSuchMethodException("There are no methods named \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        if (hasMultipleCandidates()) {
            throw new UnsupportedOperationException("Found more than one function with method name \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        this.bindingDefinitions = new HashMap<>();

        for (Map.Entry<String, BindingInfo> entry : bindingInfos.entrySet()) {
            this.bindingDefinitions.put(entry.getKey(), new BindingDefinition(entry.getKey(), entry.getValue()));
        }
    }

    private Class<?> getContainingClass(String className, ClassLoaderProvider classLoaderProvider) throws ClassNotFoundException {
        ClassLoader classLoader = classLoaderProvider.createClassLoader();
        return Class.forName(className, true, classLoader);
    }

    public Class<?> getContainingClass() {
        return containingClass;
    }

    public Map<String, BindingDefinition> getBindingDefinitions() {
        return bindingDefinitions;
    }


    synchronized void addCandidate(Method method) {
        this.candidates.add(new MethodBindInfo(method));
    }

    public synchronized boolean hasCandidates() {
        return !this.candidates.isEmpty();
    }

    public synchronized boolean hasMultipleCandidates() {
        return this.candidates.size() > 1;
    }

    public synchronized MethodBindInfo getMethodBindInfo() {
        return this.candidates.get(0);
    }
    private final List<MethodBindInfo> candidates;
}
