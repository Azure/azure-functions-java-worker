package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import org.junit.*;

import static junit.framework.TestCase.*;

import java.io.File;
import java.util.*;


import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;

public class JavaMethodExecutorTest {

	private static FunctionClassLoaderProvider functionClassLoaderProvider = new FunctionClassLoaderProvider();

	//TODO: figure out why below logics not always works on azure devops pipelines, it seems more to be a issue from
	//	azure devops pipelines. Currently, all test cases are testing the TestFunctionsClass using system classloader as
	// TestFunctionsClass is in the classpath when running the unit tests.

//	@BeforeClass
//	public static void setClassLoaderProvider() throws Exception {
//		String targetPath = JavaMethodExecutorTest.class.getProtectionDomain().getCodeSource().getLocation().getPath();
//		File testJar = new File(targetPath + "/TestFunctionsClass.jar");
//		boolean exists = testJar.exists();
//		if (!exists) {
//			WorkerLogManager.getSystemLogger().severe(testJar + "doesn't exist");
//			throw new RuntimeException("TestFunctionsClass.jar not exist");
//		}
//		functionClassLoaderProvider.addCustomerUrl(testJar.toURI().toURL());
//	}

	@Test
    public void functionMethodLoadSucceeds() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
    }
	
	@Test(expected = NoSuchMethodException.class)   
    public void functionMethodLoadFails_DoesnotExist() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }
	
	@Test(expected = UnsupportedOperationException.class)   
    public void functionMethodLoadFails_DuplicateAnnotations() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }
}
