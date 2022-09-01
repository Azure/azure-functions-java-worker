package com.microsoft.azure.functions.worker.broker;

import org.junit.*;

import static junit.framework.TestCase.*;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;


import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;

public class JavaMethodExecutorTest {

	private static FunctionClassLoaderProvider functionClassLoaderProvider = new FunctionClassLoaderProvider();

	@BeforeClass
	public static void setClassLoaderProvider() throws Exception {
		String targetPath = JavaMethodExecutorTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		File testJar = new File(targetPath + "/TestFunctionsClass.jar");
		functionClassLoaderProvider.addCustomerUrl(testJar.toURI().toURL());
		System.out.println(functionClassLoaderProvider);
		URLClassLoader classLoader = (URLClassLoader) functionClassLoaderProvider.createClassLoader();
		System.out.println(functionClassLoaderProvider.createClassLoader());
		URL[] urLs = classLoader.getURLs();
		System.out.println("begin url size: " + urLs.length);
		for (URL urL : urLs) {
			System.out.println("Before CLass url: " + urL);
		}
	}

	@Test
    public void functionMethodLoadSucceeds() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.out.println("Provider" + functionClassLoaderProvider);
		printClassLoader(functionClassLoaderProvider);
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
    }

	@Test(expected = NoSuchMethodException.class)
    public void functionMethodLoadFails_DoesnotExist() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.out.println("Provider" + functionClassLoaderProvider);
		printClassLoader(functionClassLoaderProvider);
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }

	@Test(expected = UnsupportedOperationException.class)
    public void functionMethodLoadFails_DuplicateAnnotations() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.out.println("Provider" + functionClassLoaderProvider);
		printClassLoader(functionClassLoaderProvider);
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }

	private void printClassLoader(FunctionClassLoaderProvider functionClassLoaderProvider) {
		URLClassLoader classLoader = (URLClassLoader) functionClassLoaderProvider.createClassLoader();
		System.out.println("ClassLoader: " + classLoader);
		URL[] urLs = classLoader.getURLs();
		System.out.println("URL in classloader: " + urLs.length);
		for (URL urL : urLs) {
			System.out.println("@@URL: " + urL);
		}
	}
}
