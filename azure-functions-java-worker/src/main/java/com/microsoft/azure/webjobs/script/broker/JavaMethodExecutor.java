package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.annotation.*;

import com.microsoft.azure.serverless.functions.*;

/**
 * This class is used for reflect a Java method with its parameters.
 */
public class JavaMethodExecutor {
    public JavaMethodExecutor(String jar, String fullMethodName)
            throws MalformedURLException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        this.jarPath = jar;
        this.candidates = new ArrayList<>();
        this.splitFullMethodName(fullMethodName);
        this.retrieveCandidates();
    }

    public JavaMethodOutput execute(JavaMethodInput[] inputs, ExecutionContext context)
            throws InvocationTargetException, IllegalAccessException, InstantiationException, NoSuchMethodException {
        OverloadResolver overloadResolver = new OverloadResolver(context, inputs);
        Optional<OverloadResolver.Result> result = overloadResolver.resolve(this.candidates);
        if (!result.isPresent()) {
            throw new NoSuchMethodException("Cannot locate the method signature with the given input");
        }

        Method targetMethod = result.get().getMethod();
        Object instance = Modifier.isStatic(targetMethod.getModifiers()) ? null : this.containingClass.newInstance();
        Object returnValue = targetMethod.invoke(instance, result.get().getArguments());

        // TODO: Consider multiple outputs here
        return new JavaMethodOutput(returnValue);
    }

    @PostConstruct
    private void splitFullMethodName(String fullMethodName) {
        int lastPeriodIndex = fullMethodName.lastIndexOf('.');
        if (lastPeriodIndex < 0) {
            throw new IllegalArgumentException("\"" + fullMethodName + "\" is not a qualified method name");
        }
        this.fullClassName = fullMethodName.substring(0, lastPeriodIndex);
        this.methodName = fullMethodName.substring(lastPeriodIndex + 1);
        if (this.fullClassName.isEmpty() || this.methodName.isEmpty()) {
            throw new IllegalArgumentException("\"" + fullMethodName + "\" is not a qualified method name");
        }
    }

    @PostConstruct
    private void retrieveCandidates() throws MalformedURLException, ClassNotFoundException, IllegalAccessException, NoSuchMethodException {
        URL jarUrl = Paths.get(this.jarPath).toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[] { jarUrl });
        this.containingClass = Class.forName(this.fullClassName, true, classLoader);
        for (Method method : this.containingClass.getMethods()) {
            if (method.getName().equals(this.methodName)) {
                this.candidates.add(method);
            }
        }
        if (this.candidates.isEmpty()) {
            throw new NoSuchMethodException("\"" + this.methodName + "\" not found in \"" + this.fullClassName + "\"");
        }
    }

    private String jarPath;
    private String fullClassName;
    private String methodName;
    private Class<?> containingClass;
    private List<Method> candidates;
}
