package com.microsoft.azure.functions.worker.broker;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.azure.functions.rpc.messages.*;
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
		dataStore.addTriggerMetadataSource(request.getTriggerMetadataMap());
		dataStore.addParameterSources(request.getInputDataList());
		dataStore.addExecutionContextSource(request.getInvocationId(), methodEntry.left, request.getTraceContext().getTraceParent(), request.getTraceContext().getTraceState(), request.getTraceContext().getAttributesMap());

		executor.execute(dataStore);
		outputs.addAll(dataStore.getOutputParameterBindings(true));
		return dataStore.getDataTargetTypedValue(BindingDataStore.RETURN_NAME);
	}

	public Optional<String> getMethodName(String id) {
		return Optional.ofNullable(this.methods.get(id)).map(entry -> entry.left);
	}

	private void addSearchPathsToClassLoader(FunctionMethodDescriptor function) throws IOException {
		URL jarUrl = new File(function.getJarPath()).toURI().toURL();
		classLoaderProvider.addUrl(jarUrl);
		function.getLibDirectory().ifPresent(d -> registerWithClassLoaderProvider(d));
	}

	private void registerWithClassLoaderProvider(File libDirectory) {
		try {
			classLoaderProvider.addDirectory(libDirectory);
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
		}
	}

	private final Map<String, ImmutablePair<String, JavaMethodExecutor>> methods;
	private final ClassLoaderProvider classLoaderProvider;
}
