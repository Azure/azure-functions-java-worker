package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import com.microsoft.azure.functions.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
public class JavaMethodExecutor {
    public JavaMethodExecutor(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);
        this.overloadResolver = new OverloadResolver();

        for (Method method : this.containingClass.getMethods()) {                     
            if (method.getName().equals(descriptor.getMethodName())) {
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
    
    Map<String, BindingDefinition> getBindingDefinitions() { return this.bindingDefinitions; }
    
    public OverloadResolver getOverloadResolver() { return this.overloadResolver; }

    void execute(BindingDataStore dataStore) throws Exception {
        Object retValue = this.overloadResolver.resolve(dataStore)
            .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
            .invoke(() -> this.containingClass.newInstance());
        dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
    }
    
    Class<?> getContainingClass(String className, ClassLoaderProvider classLoaderProvider) throws ClassNotFoundException {
        ClassLoader classLoader = classLoaderProvider.getClassLoader();
        return Class.forName(className, true, classLoader);
    }

    private Class<?> containingClass;
    private final OverloadResolver overloadResolver;
    private final Map<String, BindingDefinition> bindingDefinitions;
}
