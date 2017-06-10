package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;

/**
 * This class is used for reflect a Java method with its parameters.
 */
public class JavaMethodReflector {
    public JavaMethodReflector(String jarPath, String fullMethodName)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException {
        String[] classMethodName = splitFullMethodName(fullMethodName);
        Class<?> reflectedClass = loadClassFromJar(jarPath, classMethodName[0]);

        // TODO: Upgrade this
        Class<?>[] parameterTypes = new Class<?>[] { String.class };
        this.function = reflectedClass.getMethod(classMethodName[1], parameterTypes);
    }

    private Method function;

    /**
     * Split the full method name into separate names. "package.Class.method" -> ["package.Class", "method"]
     * @param fullMethodName The full method name like package.Class.method
     * @return The separated names: [0] - FullClassName; [1] - MethodName
     */
    private static String[] splitFullMethodName(String fullMethodName) {
        int lastPeriodIndex = fullMethodName.lastIndexOf('.');
        if (lastPeriodIndex < 0) {
            throw new IllegalArgumentException("No periods in fullMethodName \"" + fullMethodName + "\"");
        }
        String className = fullMethodName.substring(0, lastPeriodIndex);
        String methodName = fullMethodName.substring(lastPeriodIndex + 1);
        if (className.length() == 0 || methodName.length() == 0) {
            throw new IllegalArgumentException("fullMethodName \"" + fullMethodName + "\" can not be split");
        }
        return new String[] { className, methodName };
    }

    private static Class<?> loadClassFromJar(String jarPath, String className)
            throws MalformedURLException, ClassNotFoundException {
        URL jarUrl = Paths.get(jarPath).toUri().toURL();
        URLClassLoader classLoader = new URLClassLoader(new URL[] { jarUrl });
        return Class.forName(className, true, classLoader);
    }
}
