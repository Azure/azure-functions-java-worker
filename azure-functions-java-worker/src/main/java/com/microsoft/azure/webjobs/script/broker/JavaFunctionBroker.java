package com.microsoft.azure.webjobs.script.broker;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

import com.microsoft.azure.webjobs.script.WorkerLogManager;
import com.microsoft.azure.webjobs.script.binding.*;
import com.microsoft.azure.webjobs.script.reflect.ClassLoaderProvider;
import com.microsoft.azure.webjobs.script.reflect.FunctionDescriptor;
import com.microsoft.azure.webjobs.script.rpc.messages.*;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * A broker between JAR methods and the function RPC. It can load methods using reflection, and invoke them at runtime.
 * Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {
    public JavaFunctionBroker(ClassLoaderProvider classLoaderProvider) {
        this.methods = new ConcurrentHashMap<>();
        this.classLoaderProvider = classLoaderProvider;
    }

    public void loadMethod(FunctionDescriptor function, String methodName, Map<String, BindingInfo> bindings) 
    		throws ClassNotFoundException, NoSuchMethodException, IOException 
    {
        if (methodName == null) {
            throw new NullPointerException("methodName should not be null");
        }
        
        addSearchPathsToClassLoader(function);
        JavaMethodExecutor executor = new JavaMethodExecutor(function, methodName, bindings, classLoaderProvider);
        
        this.methods.put(function.getId(), new ImmutablePair<>(function.getName(), executor));
    }

    public Optional<TypedData> invokeMethod(String id, InvocationRequest request, List<ParameterBinding> outputs) throws Exception {
        ImmutablePair<String, JavaMethodExecutor> methodEntry = this.methods.get(id);
        JavaMethodExecutor executor = methodEntry.right;
        if (executor == null) { throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\""); }

        BindingDataStore dataStore = new BindingDataStore();
        dataStore.setBindingDefinitions(executor.getBindingDefinitions());
        dataStore.addTriggerMetadataSource(request.getTriggerMetadataMap());
        dataStore.addParameterSources(request.getInputDataList());
        dataStore.addExecutionContextSource(request.getInvocationId(), methodEntry.left);

        executor.execute(dataStore);
        outputs.addAll(dataStore.getOutputParameterBindings(true));
        return dataStore.getDataTargetTypedValue(BindingDataStore.RETURN_NAME);
    }

    public Optional<String> getMethodName(String id) {
        return Optional.ofNullable(this.methods.get(id)).map(entry -> entry.left);
    }
    
    private void addSearchPathsToClassLoader(FunctionDescriptor function) throws IOException {
    		URL jarUrl = new File(function.getJarPath()).toURI().toURL();
    		classLoaderProvider.addUrl(jarUrl);
    		function.getLibDirectory().ifPresent(d -> registerWithClassLoaderProvider(d));
    }
    
    private void registerWithClassLoaderProvider(File libDirectory) {
    		try {
			classLoaderProvider.addDirectory(libDirectory);
		} catch (Exception e) {
		}
    }

    private final Map<String, ImmutablePair<String, JavaMethodExecutor>> methods;
    private final ClassLoaderProvider classLoaderProvider;
}
