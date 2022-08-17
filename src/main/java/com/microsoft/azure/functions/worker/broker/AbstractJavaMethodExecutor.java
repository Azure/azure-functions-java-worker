package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.BindingInfo;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDefinition;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class AbstractJavaMethodExecutor implements JavaMethodExecutor {

    public static final String SERVICE_META_INF_PATH = "META-INF/service/com.microsoft.azure.functions.worker.FunctionFactory";

    public AbstractJavaMethodExecutor(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindingInfos, ClassLoaderProvider classLoaderProvider)
            throws MalformedURLException, ClassNotFoundException, NoSuchMethodException {
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
        resolveFunctionFactory();

    }


    @Override
    public boolean hasFactory() {
        return functionFactory != null;
    }

    @Override
    public Map<String, BindingDefinition> getBindingDefinitions() { return this.bindingDefinitions; }

    @Override
    public ParameterResolver getOverloadResolver() { return this.overloadResolver; }

    protected Object createFunctionInstance() throws Exception {
        if (functionFactory == null) {
            return this.containingClass.newInstance();
        }
        return functionFactory.invoke(null, containingClass);
    }

    protected Logger getLogger() {
        return WorkerLogManager.getSystemLogger();
    }

    protected void resolveFunctionFactory() throws ClassNotFoundException, NoSuchMethodException {
        InputStream is = classLoader.getResourceAsStream(SERVICE_META_INF_PATH);
        if (is == null) {
            getLogger().info("resolveFunctionFactory could not find descriptor");
            return;
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        Class factoryClass = null;
        String factoryName = null;
        try {
            factoryName = reader.readLine().trim();
            factoryClass = classLoader.loadClass(factoryName);
        } catch (ClassNotFoundException e) {
            throw new ClassNotFoundException("Could not resolve function factory class " + factoryName);
        } catch (IOException io) {
            throw new ClassNotFoundException("Could not resolve function factory class");
        }
        try {
            functionFactory = factoryClass.getMethod("newInstance", Class.class);
        } catch (NoSuchMethodException e) {
            throw new NoSuchMethodException("function factory class must have a static \"newInstance\" method that takes one Class parameter and returns an Object");
        }
        try {
            is.close();
        } catch (IOException e) {
            // ignore
        }
        getLogger().info("Using " + factoryName + " to instantiate your function class");
    }

    abstract protected Class<?> getContainingClass(String className) throws ClassNotFoundException;




    protected ClassLoader classLoader;
    protected Class<?> containingClass;
    protected Method functionFactory;
    protected final ParameterResolver overloadResolver;
    protected final Map<String, BindingDefinition> bindingDefinitions;
}
