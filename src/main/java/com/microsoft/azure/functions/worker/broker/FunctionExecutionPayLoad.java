package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.BindingInfo;
import com.microsoft.azure.functions.worker.binding.BindingDefinition;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class FunctionExecutionPayLoad {
    private final Class<?> containingClass;
    private final ParameterResolver overloadResolver;
    private final Map<String, BindingDefinition> bindingDefinitions;

    public FunctionExecutionPayLoad(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);
        this.overloadResolver = new ParameterResolver();

        for (Method method : this.containingClass.getMethods()) {
            if (method.getDeclaringClass().getName().equals(descriptor.getFullClassName()) && method.getName().equals(descriptor.getMethodName())) {
                this.overloadResolver.addCandidate(method);
            }
        }

        if (!this.overloadResolver.hasCandidates()) {
            throw new NoSuchMethodException("There are no methods named \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        if (this.overloadResolver.hasMultipleCandidates()) {
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

    public ParameterResolver getOverloadResolver() {
        return overloadResolver;
    }

    public Map<String, BindingDefinition> getBindingDefinitions() {
        return bindingDefinitions;
    }
}
