package com.microsoft.azure.functions.worker.broker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.microsoft.azure.functions.internal.spi.middleware.Middleware;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.spi.inject.FunctionInstanceInjector;
import com.microsoft.azure.functions.worker.Constants;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionContextDataSource;
import com.microsoft.azure.functions.worker.binding.ExecutionRetryContext;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.chain.FunctionExecutionMiddleware;
import com.microsoft.azure.functions.worker.chain.InvocationChainFactory;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

/**
 * A broker between JAR methods and the function RPC. It can load methods using
 * reflection, and invoke them at runtime. Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {

	//TODO: build dedicate ImmutablePair class with meaningful fields.
	private final Map<String, ImmutablePair<String, FunctionDefinition>> methods;
	private final ClassLoaderProvider classLoaderProvider;
	private String workerDirectory;
	private volatile boolean oneTimeLogicInitialized = false;
	private volatile InvocationChainFactory invocationChainFactory;
	private volatile FunctionInstanceInjector functionInstanceInjector;
	private final Object oneTimeLogicInitializationLock = new Object();

	private FunctionInstanceInjector newInstanceInjector() {
		return new FunctionInstanceInjector() {
			@Override
			public <T> T getInstance(Class<T> functionClass) throws Exception {
				return functionClass.newInstance();
			}
		};
	}

	public JavaFunctionBroker(ClassLoaderProvider classLoaderProvider) {
		this.methods = new ConcurrentHashMap<>();
		this.classLoaderProvider = classLoaderProvider;
	}

	public void loadMethod(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindings)
			throws ClassNotFoundException, NoSuchMethodException, IOException {
		descriptor.validate();
		addSearchPathsToClassLoader(descriptor);
		initializeOneTimeLogics();
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, classLoaderProvider);
		this.methods.put(descriptor.getId(), new ImmutablePair<>(descriptor.getName(), functionDefinition));
	}

	private void initializeOneTimeLogics() {
		if (!oneTimeLogicInitialized) {
			synchronized (oneTimeLogicInitializationLock) {
				if (!oneTimeLogicInitialized) {
					initializeInvocationChainFactory();
					initializeFunctionInstanceInjector();
					oneTimeLogicInitialized = true;
				}
			}
		}
	}

	private void initializeInvocationChainFactory() {
		ArrayList<Middleware> middlewares = new ArrayList<>();
		ClassLoader prevContextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			//ServiceLoader will use thread context classloader to verify loaded class
			Thread.currentThread().setContextClassLoader(classLoaderProvider.createClassLoader());
			for (Middleware middleware : ServiceLoader.load(Middleware.class)) {
				middlewares.add(middleware);
				WorkerLogManager.getSystemLogger().info("Load middleware " + middleware.getClass().getSimpleName());
			}
		} finally {
			Thread.currentThread().setContextClassLoader(prevContextClassLoader);
		}
		middlewares.add(getFunctionExecutionMiddleWare());
		this.invocationChainFactory = new InvocationChainFactory(middlewares);
	}

	private void initializeFunctionInstanceInjector() {
		ClassLoader prevContextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			//ServiceLoader will use thread context classloader to verify loaded class
			Thread.currentThread().setContextClassLoader(classLoaderProvider.createClassLoader());
			Iterator<FunctionInstanceInjector> iterator = ServiceLoader.load(FunctionInstanceInjector.class).iterator();
			if (iterator.hasNext()) {
				this.functionInstanceInjector = iterator.next();
				WorkerLogManager.getSystemLogger().info("Load function instance injector: " + this.functionInstanceInjector.getClass().getName());
				if (iterator.hasNext()){
					WorkerLogManager.getSystemLogger().warning("Customer function app has multiple FunctionInstanceInjector implementations.");
					throw new RuntimeException("Customer function app has multiple FunctionInstanceInjector implementations");
				}
			}else {
				this.functionInstanceInjector = newInstanceInjector();
				WorkerLogManager.getSystemLogger().info("Didn't find any function instance injector, creating function class instance every invocation.");
			}
		} finally {
			Thread.currentThread().setContextClassLoader(prevContextClassLoader);
		}
	}

	private FunctionExecutionMiddleware getFunctionExecutionMiddleWare() {
		FunctionExecutionMiddleware functionExecutionMiddleware = new FunctionExecutionMiddleware(
				JavaMethodExecutors.createJavaMethodExecutor(this.classLoaderProvider.createClassLoader()));
		WorkerLogManager.getSystemLogger().info("Load last middleware: FunctionExecutionMiddleware");
		return functionExecutionMiddleware;
	}

	public Optional<TypedData> invokeMethod(String id, InvocationRequest request, List<ParameterBinding> outputs)
			throws Exception {
		ExecutionContextDataSource executionContextDataSource = buildExecutionContext(id, request);
		this.invocationChainFactory.create().doNext(executionContextDataSource);
		outputs.addAll(executionContextDataSource.getDataStore().getOutputParameterBindings(true));
		return executionContextDataSource.getDataStore().getDataTargetTypedValue(BindingDataStore.RETURN_NAME);
	}

	private ExecutionContextDataSource buildExecutionContext(String id,  InvocationRequest request)
			throws NoSuchMethodException {
		ImmutablePair<String, FunctionDefinition> methodEntry = this.methods.get(id);
		FunctionDefinition functionDefinition = methodEntry.right;
		if (functionDefinition == null) {
			throw new NoSuchMethodException("Cannot find method with ID \"" + id + "\"");
		}
		BindingDataStore dataStore = new BindingDataStore();
		dataStore.setBindingDefinitions(functionDefinition.getBindingDefinitions());
		dataStore.addTriggerMetadataSource(getTriggerMetadataMap(request));
		dataStore.addParameterSources(request.getInputDataList());
		ExecutionTraceContext traceContext = new ExecutionTraceContext(request.getTraceContext().getTraceParent(),
				request.getTraceContext().getTraceState(), request.getTraceContext().getAttributesMap());
		ExecutionRetryContext retryContext = new ExecutionRetryContext(request.getRetryContext().getRetryCount(),
				request.getRetryContext().getMaxRetryCount(), request.getRetryContext().getException());
		ExecutionContextDataSource executionContextDataSource = new ExecutionContextDataSource(request.getInvocationId(),
				traceContext, retryContext, methodEntry.left, dataStore, functionDefinition.getCandidate(),
				functionDefinition.getContainingClass(), request.getInputDataList(), this.functionInstanceInjector);
		dataStore.addExecutionContextSource(executionContextDataSource);
		return executionContextDataSource;
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
}
