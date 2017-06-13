package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.annotation.*;

/**
 * This class is used for reflect a Java method with its parameters.
 */
public class JavaMethodExecutor {
    public JavaMethodExecutor(String jar, String fullMethodName)
            throws MalformedURLException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        this.jarPath = jar;
        this.candidates = new ArrayList<>();
        this.splitFullMethodName(fullMethodName);
        this.retrieveCandidates();
    }

    public JavaMethodOutput execute(JavaMethodInput[] inputs) throws InvocationTargetException, IllegalAccessException {
        Object[] parameters = Arrays.stream(inputs).map(JavaMethodInput::getValue).toArray();

        // TODO: Get a more precise method overload from inputs types
        Method targetMethod = this.candidates.get(0);
        Object instance = Modifier.isStatic(targetMethod.getModifiers()) ? null : this.classInstance;
        Object returnValue = targetMethod.invoke(instance, parameters);

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
    private void retrieveCandidates() throws MalformedURLException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        URL jarUrl = Paths.get(this.jarPath).toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[] { jarUrl });
        Class<?> reflectedClass = Class.forName(this.fullClassName, true, classLoader);
        for (Method method : reflectedClass.getMethods()) {
            if (method.getName().equals(this.methodName)) {
                this.candidates.add(method);
            }
        }
        if (this.candidates.isEmpty()) {
            throw new NoSuchMethodError("\"" + this.methodName + "\" not found in \"" + this.fullClassName + "\"");
        }
        if (this.candidates.stream().allMatch((m) -> Modifier.isStatic(m.getModifiers()))) {
            this.classInstance = null;
        } else {
            this.classInstance = reflectedClass.newInstance();
        }
    }

    private String jarPath;
    private String fullClassName;
    private String methodName;
    private Object classInstance;
    private List<Method> candidates;
}
