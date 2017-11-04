package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

import org.apache.commons.lang3.*;

import com.microsoft.azure.serverless.functions.annotation.*;

import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * Used to executor of arbitrary Java method in any JAR using reflection.
 * Thread-Safety: Multiple thread.
 */
class JavaMethodExecutor {
    JavaMethodExecutor(String funcname, String jar, String fullMethodName, Map<String, BindingInfo> bindingInfos)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException {
        String jarPath = StringUtils.trim(jar);
        if (StringUtils.isBlank(jarPath)) {
            throw new IllegalArgumentException("\"" + jar + "\" is not a qualified JAR file name");
        }
        URL jarUrl = Paths.get(jarPath).toUri().toURL();
        URLClassLoader jarLoader = new URLClassLoader(new URL[] { jarUrl });

        String fullClassName = StringUtils.trim(StringUtils.substringBeforeLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
        String methodName = StringUtils.trim(StringUtils.substringAfterLast(fullMethodName, ClassUtils.PACKAGE_SEPARATOR));
        if (StringUtils.isAnyBlank(fullClassName, methodName)) {
            throw new IllegalArgumentException("\"" + fullMethodName + "\" is not a qualified full Java method name");
        }

        this.containingClass = Class.forName(fullClassName, true, jarLoader);
        this.overloadResolver = new OverloadResolver();
        for (Method method : this.containingClass.getMethods()) {
            FunctionName annotatedName = method.getAnnotation(FunctionName.class);
            if (method.getName().equals(methodName) && (annotatedName == null || annotatedName.value().equals(funcname))) {
                this.overloadResolver.addCandidate(method);
            }
        }

        if (!this.overloadResolver.hasCandidates()) {
            throw new NoSuchMethodException("There are no methods named \"" + methodName + "\" in class \"" + fullClassName + "\"");
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

    private Class<?> containingClass;
    private final OverloadResolver overloadResolver;
    private final Map<String, BindingDefinition> bindingDefinitions;
}
