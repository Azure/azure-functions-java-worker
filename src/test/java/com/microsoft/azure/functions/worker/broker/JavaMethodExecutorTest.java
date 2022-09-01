package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.junit.*;

import static junit.framework.TestCase.*;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;


import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;

public class JavaMethodExecutorTest {

	private static FunctionClassLoaderProvider functionClassLoaderProvider = new FunctionClassLoaderProvider();

	//TODO: figure out why below logics not always works on azure devops pipelines, it seems more to be a issue from
	//	azure devops pipelines. Currently, all test cases are testing the TestFunctionsClass using system classloader as
	// TestFunctionsClass is in the classpath when running the unit tests.

	@BeforeClass
	public static void setClassLoaderProvider() throws Exception {
		String targetPath = JavaMethodExecutorTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		File testJar = new File(targetPath + "/TestFunctionsClass.jar");
		System.out.println("@@Jar file location: " + testJar);
		boolean exists = testJar.exists();
		if (!exists) {
			WorkerLogManager.getSystemLogger().severe(testJar + "doesn't exist");
			throw new RuntimeException("TestFunctionsClass.jar not exist");
		}
		functionClassLoaderProvider.addCustomerUrl(testJar.toURI().toURL());
//		URL[] urls = new URL[1];
//		urls[0] = testJar.toURI().toURL();
//		URLClassLoader urlClassLoader = new URLClassLoader(urls);
//		Class<?> aClass = Class.forName("com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass", false, urlClassLoader);
//		Object o = aClass.newInstance();
//		aClass.getDeclaredMethod()
	}

	@Test
	public void testUrlClassLoader() throws Exception {
		String targetPath = JavaMethodExecutorTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		File testJar = new File(targetPath + "/TestFunctionsClass.jar");
		URL[] urls = new URL[1];
		urls[0] = testJar.toURI().toURL();
		URLClassLoader urlClassLoader = new URLClassLoader(urls);
		Class<?> containingClass = Class.forName("com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass", false, urlClassLoader);
		List<Method> res = new ArrayList<>();

		for (Method method : containingClass.getMethods()) {
			if (method.getDeclaringClass().getName().equals("com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass") && method.getName().equals("TestHttpTrigger")) {
				res.add(method);
			}
		}

		if (res.isEmpty()) {
			throw new NoSuchMethodException("There are no methods named TestHttpTrigger in class TestFunctionsClass");
		}

		if (res.size() > 1) {
			throw new UnsupportedOperationException("Found more than one function with method name TestHttpTrigger in class TestFunctionsClass");
		}

		System.out.println("Method numbers in method list: " + res.size());
	}

	@Test
    public void functionMethodLoadSucceeds() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
    }
	
	@Test(expected = NoSuchMethodException.class)   
    public void functionMethodLoadFails_DoesnotExist() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }
	
	@Test(expected = UnsupportedOperationException.class)   
    public void functionMethodLoadFails_DuplicateAnnotations() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }
}
