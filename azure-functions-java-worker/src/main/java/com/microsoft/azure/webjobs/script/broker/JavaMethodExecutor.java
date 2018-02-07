package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.microsoft.azure.serverless.functions.annotation.*;

import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.reflect.ClassLoaderProvider;
import com.microsoft.azure.webjobs.script.reflect.FunctionDescriptor;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
class JavaMethodExecutor {
    JavaMethodExecutor(FunctionDescriptor function, String fullMethodName, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException 
    {
    		MethodInfo methodInfo = new MethodInfo(fullMethodName);
    		methodInfo.verifyMethodToBeExecuted();

        this.containingClass = getContainingClass(methodInfo.fullClassName, classLoaderProvider);
        this.overloadResolver = new OverloadResolver();
        
        for (Method method : this.containingClass.getMethods()) {
            FunctionName annotatedName = method.getAnnotation(FunctionName.class);
            
            if (method.getName().equals(methodInfo.name) && (annotatedName == null || annotatedName.value().equals(function.getName()))) {
                this.overloadResolver.addCandidate(method);
            }
        }

        if (!this.overloadResolver.hasCandidates()) {
            throw new NoSuchMethodException("There are no methods named \"" + methodInfo.name + "\" in class \"" + methodInfo.fullClassName + "\"");
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
    
    /*
     * "struct" to track the info on the function method
     */
    private final class MethodInfo {
    		public String fullClassName;
    		public String fullName;
    		public String name;
    		
    		public MethodInfo(String fullMethodName) {
    			this.fullName = fullMethodName;
    			this.fullClassName = StringUtils.trim(StringUtils.substringBeforeLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
    			this.name = StringUtils.trim(StringUtils.substringAfterLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
    		}
    		
    	    void verifyMethodToBeExecuted() {
    	        if (StringUtils.isAnyBlank(fullClassName, this.name)) {
    	            throw new IllegalArgumentException("\"" + this.fullName + "\" is not a qualified full Java method name");
    	        }
    	    }
    }
}
