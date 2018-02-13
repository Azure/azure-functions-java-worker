package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.util.*;

import com.microsoft.azure.serverless.functions.annotation.*;
import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.description.*;
import com.microsoft.azure.webjobs.script.reflect.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
class JavaMethodExecutor {
    JavaMethodExecutor(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException 
    {
        descriptor.validateMethodInfo();

        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);
        this.overloadResolver = new OverloadResolver();
        
        for (Method method : this.containingClass.getMethods()) {
            FunctionName annotatedName = method.getAnnotation(FunctionName.class);
            
            if (method.getName().equals(descriptor.getMethodName()) && (annotatedName == null || annotatedName.value().equals(descriptor.getName()))) {
                this.overloadResolver.addCandidate(method);
            }
        }

        if (!this.overloadResolver.hasCandidates()) {
            throw new NoSuchMethodException("There are no methods named \"" + descriptor.getName() + "\" in class \"" + descriptor.getFullClassName() + "\"");
        }

        this.bindingDefinitions = new HashMap<>();
        
        for (Map.Entry<String, BindingInfo> entry : bindingInfos.entrySet()) {
            this.bindingDefinitions.put(entry.getKey(), new BindingDefinition(entry.getKey(), entry.getValue()));
        }
    }
    
    Map<String, BindingDefinition> getBindingDefinitions() { return this.bindingDefinitions; }

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
