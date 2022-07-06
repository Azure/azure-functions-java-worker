package com.microsoft.azure.functions.worker.broker;

import com.kc.DepTool;
import com.microsoft.azure.functions.rpc.messages.BindingInfo;
import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.Constants;
import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.worker.binding.ExecutionRetryContext;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import com.microsoft.azure.functions.worker.description.FunctionMethodDescriptor;
import com.microsoft.azure.functions.worker.reflect.ClassLoaderProvider;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * A broker between JAR methods and the function RPC. It can load methods using
 * reflection, and invoke them at runtime. Thread-Safety: Multiple thread.
 */
public class JavaFunctionBroker {
	public JavaFunctionBroker(ClassLoaderProvider classLoaderProvider) {
		WorkerLogManager.getSystemLogger().info("this is java11 JavaFunctionBroker.class" + DepTool.printVersion());
		this.methods = new ConcurrentHashMap<>();
		this.classLoaderProvider = classLoaderProvider;
	}

	public void loadMethod(FunctionMethodDescriptor descriptor, Map<String, BindingInfo> bindings)
			throws ClassNotFoundException, NoSuchMethodException, IOException {
		descriptor.validate();

		addSearchPathsToClassLoader(descriptor);
//		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, classLoaderProvider);

		JavaMethodExecutor executor = new JavaMethodExecutorImpl(descriptor, bindings, classLoaderProvider);

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

		ExecutionTraceContext traceContext = new ExecutionTraceContext(request.getTraceContext().getTraceParent(), request.getTraceContext().getTraceState(), request.getTraceContext().getAttributesMap());
		ExecutionRetryContext retryContext = new ExecutionRetryContext(request.getRetryContext().getRetryCount(), request.getRetryContext().getMaxRetryCount(), request.getRetryContext().getException());

		dataStore.addExecutionContextSource(request.getInvocationId(), methodEntry.left, traceContext, retryContext);

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
		classLoaderProvider.addCustomerUrl(jarUrl);
		if(function.getLibDirectory().isPresent()) {
			registerWithClassLoaderProvider(function.getLibDirectory().get());
		}else{
			registerJavaLibrary();
		}
		createCxModuleLayer(function);
	}

	//Create a module layer for customer so that customer code and dependencies are isolated from boot layer (where our code running on)
	private void createCxModuleLayer(FunctionMethodDescriptor function) {
		Path jarPath = Paths.get(function.getJarPath());
		ModuleFinder jarFinder = ModuleFinder.of(jarPath);
		Set<ModuleReference> moduleSet = jarFinder.findAll();
		this.cxModuleName = moduleSet.iterator().next().descriptor().name();
		Path libPath = Paths.get(function.getLibDirectory().get().getAbsolutePath());
		ModuleFinder libFinder = ModuleFinder.of(libPath);
		ModuleFinder beforeFinder = ModuleFinder.compose(jarFinder, libFinder);

		//TODO: should we find all cx dependencies or just gson in cx module Layer?
//		Path gsonPath = Paths.get(function.getLibDirectory().get().getAbsolutePath() + "/com.google.gson");
//		ModuleFinder gsonFinder = ModuleFinder.of(gsonPath);
//		ModuleFinder beforeFinder = ModuleFinder.compose(jarFinder, gsonFinder);

		Set<ModuleReference> all = beforeFinder.findAll();
		Set<String> root = all.stream().map(reference -> reference.descriptor().name()).collect(Collectors.toSet());
		ModuleLayer parentLayer = ModuleLayer.boot();
		Configuration parentConfig = parentLayer.configuration();
		Configuration configuration = parentConfig.resolve(beforeFinder, ModuleFinder.of(), root);
//		this.cxModuleLayer = parentLayer.defineModulesWithOneLoader(configuration, classLoaderProvider.createClassLoader());
		this.cxModuleLayer = parentLayer.defineModules(configuration, s -> classLoaderProvider.createClassLoader());
		if (this.cxModuleLayer == null) throw new RuntimeException("cx module layer created failed.");
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
		String javaLibPath = System.getenv(Constants.FUNCTIONS_WORKER_DIRECTORY) + Constants.JAVA_LIBRARY_DIRECTORY;
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
	private ModuleLayer cxModuleLayer;
	private String cxModuleName;
}
