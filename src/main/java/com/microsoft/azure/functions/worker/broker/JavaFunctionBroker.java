package com.microsoft.azure.functions.worker.broker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.azure.functions.middleware.FunctionWorkerMiddleware;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.Constants;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.pipeline.DefaultInvocationPipelineBuilder;
import com.microsoft.azure.functions.worker.pipeline.FunctionExecutionMiddleware;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * A broker between JAR methods and the function RPC. It can load methods using
 * reflection, and invoke them at runtime. Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {
	public JavaFunctionBroker(ClassLoaderProvider classLoaderProvider, DefaultInvocationPipelineBuilder functionWorkerPipelineBuilder) {
		this.methods = new ConcurrentHashMap<>();
		this.classLoaderProvider = classLoaderProvider;
		this.functionWorkerPipelineBuilder = functionWorkerPipelineBuilder;
	}

	public void loadMethod(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindings)
			throws ClassNotFoundException, NoSuchMethodException, IOException {
		descriptor.validate();
		addSearchPathsToClassLoader(descriptor);
//		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, classLoaderProvider);
		FunctionExecutionPayLoad functionExecutionPayLoad = new FunctionExecutionPayLoad(descriptor, bindings, classLoaderProvider);
		loadMiddleware();
		this.methods.put(descriptor.getId(), new ImmutablePair<>(descriptor.getName(), functionExecutionPayLoad));
	}

	private void loadMiddleware() {
		if (loadMiddleware) {
			synchronized (JavaFunctionBroker.class){
				if (loadMiddleware) {
					try {
						Thread.currentThread().setContextClassLoader(classLoaderProvider.createClassLoader());
						ServiceLoader<FunctionWorkerMiddleware> middlewareServiceLoader = ServiceLoader.load(FunctionWorkerMiddleware.class);
						for (FunctionWorkerMiddleware middleware : middlewareServiceLoader) {
							this.functionWorkerPipelineBuilder.use(middleware);
						}
					} finally {
						Thread.currentThread().setContextClassLoader(ClassLoader.getSystemClassLoader());
					}
					loadFunctionExecutionMiddleWare();
					loadMiddleware = false;
				}
			}
		}
	}

	private void loadFunctionExecutionMiddleWare() {
		this.functionWorkerPipelineBuilder.use(new FunctionExecutionMiddleware());
	}

	public Optional<TypedData> invokeMethod(String id, InvocationRequest request, List<ParameterBinding> outputs)
			throws Exception {
		ImmutablePair<String, FunctionExecutionPayLoad> methodEntry = this.methods.get(id);
		FunctionExecutionPayLoad payLoad = methodEntry.right;
		if (payLoad == null) {
			throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\"");
		}
		final BindingDataStore dataStore = new BindingDataStore();
		dataStore.setBindingDefinitions(payLoad.getBindingDefinitions());
		dataStore.addTriggerMetadataSource(getTriggerMetadataMap(request));
		dataStore.addParameterSources(request.getInputDataList());
		ExecutionTraceContext traceContext = new ExecutionTraceContext(request.getTraceContext().getTraceParent(), request.getTraceContext().getTraceState(), request.getTraceContext().getAttributesMap());
		ExecutionRetryContext retryContext = new ExecutionRetryContext(request.getRetryContext().getRetryCount(), request.getRetryContext().getMaxRetryCount(), request.getRetryContext().getException());
		ExecutionContextDataSource executionContextDataSource = new ExecutionContextDataSource(request.getInvocationId(), methodEntry.left, traceContext, retryContext);
		dataStore.addExecutionContextSource(executionContextDataSource);
		executionContextDataSource.setDataStore(dataStore);
		executionContextDataSource.setMethodBindInfo(payLoad.getOverloadResolver().getMethodBindInfo());
		executionContextDataSource.setContainingClass(payLoad.getContainingClass());
		this.functionWorkerPipelineBuilder.build().doNext(executionContextDataSource);
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
		classLoaderProvider.addCustomerUrl(jarUrl);
		if(function.getLibDirectory().isPresent()) {
			registerWithClassLoaderProvider(function.getLibDirectory().get());
		}else{
			registerJavaLibrary();
		}
	}

	void registerWithClassLoaderProvider(File libDirectory) {
		try {
			addDirectory(libDirectory);
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
		}
	}

	void registerJavaLibrary(){
		try {
			if (!isTesting()){
				addJavaAnnotationLibrary();
			}
		} catch (Exception ex) {
			ExceptionUtils.rethrow(ex);
		}
	}

	void addDirectory(File directory) throws IOException {
		if (!directory.exists()) {
			return;
		}
		File[] jarFiles = directory.listFiles(file -> file.isFile() && file.getName().endsWith(".jar"));
		for (File file : jarFiles){
			classLoaderProvider.addCustomerUrl(file.toURI().toURL());
		}
		addJavaAnnotationLibrary();
	}

	public void addJavaAnnotationLibrary() throws IOException {
		String javaLibPath = workerDirectory + Constants.JAVA_LIBRARY_DIRECTORY;
		File javaLib = new File(javaLibPath);
		if (!javaLib.exists()) throw new FileNotFoundException("Error loading java annotation library jar, location doesn't exist: " + javaLibPath);
		File[] files = javaLib.listFiles(file -> file.getName().contains(Constants.JAVA_LIBRARY_ARTIFACT_ID) && file.getName().endsWith(".jar"));
		if (files.length == 0) throw new FileNotFoundException("Error loading java annotation library jar, no jar find from path:  " + javaLibPath);
		if (files.length > 1) throw new FileNotFoundException("Error loading java annotation library jar, multiple jars find from path:  " + javaLibPath);
		classLoaderProvider.addWorkerUrl(files[0].toURI().toURL());
	}

	private boolean isTesting(){
		if(System.getProperty("azure.functions.worker.java.skip.testing") != null
				&& System.getProperty("azure.functions.worker.java.skip.testing").equals("true")) {
			return true;
		} else {
			return false;
		}
	}

	public void setWorkerDirectory(String workerDirectory) {
		this.workerDirectory = workerDirectory;
	}

	private final Map<String, ImmutablePair<String, FunctionExecutionPayLoad>> methods;
//	private Map<String, String> methodNames;
	private final ClassLoaderProvider classLoaderProvider;
	private String workerDirectory;

	private final DefaultInvocationPipelineBuilder functionWorkerPipelineBuilder;
	private volatile boolean loadMiddleware = true;

	private Class<?> containingClass;
	private ParameterResolver overloadResolver;
	private Map<String, BindingDefinition> bindingDefinitions;
}
