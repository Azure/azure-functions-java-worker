package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class EnhancedJavaMethodExecutorImpl implements JavaMethodExecutor {
    public EnhancedJavaMethodExecutorImpl(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.classLoader = classLoaderProvider.createClassLoader();
        this.containingClass = getContainingClass(descriptor.getFullClassName());
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

    public Map<String, BindingDefinition> getBindingDefinitions() { return this.bindingDefinitions; }

    public ParameterResolver getOverloadResolver() { return this.overloadResolver; }

    public void execute(BindingDataStore dataStore) throws Exception {
        try {
            Thread.currentThread().setContextClassLoader(this.classLoader);
            Object retValue = this.overloadResolver.resolve(dataStore)
                    .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
                    .invoke(() -> this.containingClass.newInstance());
            dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
        } finally {
            Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
        }
    }

    private Class<?> getContainingClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, this.classLoader);
    }

    private final Class<?> containingClass;
    private final ClassLoader classLoader;
    private final ParameterResolver overloadResolver;
    private final Map<String, BindingDefinition> bindingDefinitions;
}
