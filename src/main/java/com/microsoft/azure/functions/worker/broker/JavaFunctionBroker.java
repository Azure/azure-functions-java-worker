package com.microsoft.azure.functions.worker.broker;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.Constants;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * A broker between JAR methods and the function RPC. It can load methods using
 * reflection, and invoke them at runtime. Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {
	public JavaFunctionBroker(ClassLoaderProvider classLoaderProvider) {
		this.methods = new ConcurrentHashMap<>();
		this.classLoaderProvider = classLoaderProvider;
	}

	public void loadMethod(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindings)
			throws ClassNotFoundException, NoSuchMethodException, IOException {
		descriptor.validate();

		addSearchPathsToClassLoader(descriptor);
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, classLoaderProvider);

		this.methods.put(descriptor.getId(), new ImmutablePair<>(descriptor.getName(), executor));
	}

	public Optional<TypedData> invokeMethod(String id, InvocationRequest request, List<ParameterBinding> outputs)
			throws Exception {
		ImmutablePair<String, JavaMethodExecutor> methodEntry = this.methods.get(id);
		JavaMethodExecutor executor = methodEntry.right;
		if (executor == null) {
			throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\"");
		}

		BindingDataStore dataStore = new BindingDataStore();
		dataStore.setBindingDefinitions(executor.getBindingDefinitions());
		dataStore.addTriggerMetadataSource(getTriggerMetadataMap(request));
		dataStore.addParameterSources(request.getInputDataList());
		dataStore.addExecutionContextSource(request.getInvocationId(), methodEntry.left, request.getTraceContext().getTraceParent(), request.getTraceContext().getTraceState(), request.getTraceContext().getAttributesMap());

		executor.execute(dataStore);
		outputs.addAll(dataStore.getOutputParameterBindings(true));
		return dataStore.getDataTargetTypedValue(BindingDataStore.RETURN_NAME);
	}

	public Optional<String> getMethodName(String id) {
		return Optional.ofNullable(this.methods.get(id)).map(entry -> entry.left);
	}

	// TODO the scope should be package private for testability. Modify the package name as same as main package
	public Map<String, TypedData> getTriggerMetadataMap(InvocationRequest request) {
		String    name ="";
		TypedData dataWithHttp = null;
		for(ParameterBinding e: request.getInputDataList()) {
			if (e.getData().hasHttp()) {
				dataWithHttp = e.getData();
				name = e.getName();
				break;
			}
		}

		Map<String, TypedData> triggerMetadata = new HashMap(request.getTriggerMetadataMap());
		if (!name.isEmpty() && !triggerMetadata.containsKey(name)) {
			triggerMetadata.put(name, dataWithHttp);
		}
		String requestKey = Constants.TRIGGER_METADATA_DOLLAR_REQUEST_KEY;
		if (dataWithHttp != null & !triggerMetadata.containsKey(requestKey)) {
			triggerMetadata.put(requestKey, dataWithHttp);
		}
		return Collections.unmodifiableMap(triggerMetadata);
	}

	private void addSearchPathsToClassLoader(FunctionMethodDescriptor function) throws IOException {
		URL jarUrl = new File(function.getJarPath()).toURI().toURL();
		classLoaderProvider.addUrl(jarUrl);
		if(function.getLibDirectory().isPresent()) {
			registerWithClassLoaderProvider(function.getLibDirectory().get());
		}
	}

	void registerWithClassLoaderProvider(File libDirectory) {
		try {
			classLoaderProvider.addDirectory(libDirectory);
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
		}
	}

	void verifyLibrariesExist (File workerLib, String workerLibPath) throws FileNotFoundException{
		if(!workerLib.exists()) {
			throw new FileNotFoundException("Error loading worker jars, from path:  " + workerLibPath);
		} else {
			File[] jarFiles = workerLib.listFiles(new FileFilter() {
				@Override
				public boolean accept(File file) {
					return file.isFile() && file.getName().endsWith(".jar");
				}
			});
			if(jarFiles.length == 0) {
				throw new FileNotFoundException("Error loading worker jars, from path:  " + workerLibPath + ". Jars size is zero");
			}
		}
	}

	private final Map<String, ImmutablePair<String, JavaMethodExecutor>> methods;
	private final ClassLoaderProvider classLoaderProvider;
}
