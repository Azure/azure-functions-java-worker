package com.microsoft.azure.functions.worker.broker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.*;
import java.net.URL;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import com.microsoft.azure.functions.worker.WorkerLogManager;
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
            throws IOException, ClassNotFoundException, NoSuchMethodException
    {
        descriptor.validateMethodInfo();

        this.containingClass = getContainingClass(descriptor.getFullClassName(), classLoaderProvider);
        this.overloadResolver = new ParameterResolver();

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
        resolveFunctionFactory();
    }

    // TODO remove once I figure out how to see logging in local docker
    private static Logger fileLogger;
    static {
        fileLogger = Logger.getAnonymousLogger();
        fileLogger.setUseParentHandlers(false);
        FileHandler fh = null;
        try {
            fh = new FileHandler("java_worker.log");
        } catch (IOException e) {
            e.printStackTrace();
        }
        fh.setFormatter(new SimpleFormatter());
        fh.setLevel(Level.ALL);
        fileLogger.addHandler(fh);
        fileLogger.info("INITIALIZED LOGGER");

    }
    private Logger getLogger() {
        return fileLogger;
    }

    Map<String, BindingDefinition> getBindingDefinitions() { return this.bindingDefinitions; }
    
    public ParameterResolver getOverloadResolver() { return this.overloadResolver; }

    void execute(BindingDataStore dataStore) throws Exception {
        Object retValue = this.overloadResolver.resolve(dataStore)
            .orElseThrow(() -> new NoSuchMethodException("Cannot locate the method signature with the given input"))
            .invoke(() -> createFunctionInstance());
        dataStore.setDataTargetValue(BindingDataStore.RETURN_NAME, retValue);
    }

    private Object createFunctionInstance() throws Exception {
        if (functionFactory == null) {
            return this.containingClass.newInstance();
        }
        return functionFactory.invoke(null, containingClass);
    }

    Class<?> getContainingClass(String className, ClassLoaderProvider classLoaderProvider) throws ClassNotFoundException {
        ClassLoader classLoader = classLoaderProvider.getClassLoader();
        return Class.forName(className, true, classLoader);
    }

    private void resolveFunctionFactory() throws IOException, ClassNotFoundException, NoSuchMethodException {
        ClassLoader cl = containingClass.getClassLoader();
        InputStream is = cl.getResourceAsStream("META-INF/service/com.microsoft.azure.functions.FunctionFactory");
        if (is == null) {
            getLogger().info("resolveFunctionFactory could not find descriptor");
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String factoryName = reader.readLine().trim();
        Class factoryClass = cl.loadClass(factoryName);
        functionFactory = factoryClass.getMethod("newInstance", Class.class);
        is.close();
    }


    private Method functionFactory;
    private Class<?> containingClass;
    private final ParameterResolver overloadResolver;
    private final Map<String, BindingDefinition> bindingDefinitions;
}
