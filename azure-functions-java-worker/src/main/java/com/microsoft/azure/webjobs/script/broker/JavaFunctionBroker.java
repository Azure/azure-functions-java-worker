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
            throws ClassNotFoundException, NoSuchMethodException, IOException {
    	
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
    
    void addSearchPathsToClassLoader(FunctionDescriptor function) throws IOException {
    		String jarPath = function.getJarPath();
    		
    		//look for /lib folder
    		File jarFile = new File(jarPath);
    		File jarParent = jarFile.getAbsoluteFile().getParentFile();
    		
    		if (jarParent.isDirectory()) {
    			File[] directories = jarParent.listFiles(new FileFilter() {
    			    @Override
    			    public boolean accept(File file) {
    			        return file.isDirectory() && file.getName().endsWith("lib");
    			    }
    			});
    			
    			if (directories.length > 0) {
    				classLoaderProvider.addSearchPath(directories[0].getAbsolutePath());
    			}
    		}
    		classLoaderProvider.addSearchPath(jarPath);
    }

    private final Map<String, ImmutablePair<String, JavaMethodExecutor>> methods;
    private final ClassLoaderProvider classLoaderProvider;
}
