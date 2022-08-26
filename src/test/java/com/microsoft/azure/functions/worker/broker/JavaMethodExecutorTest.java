package com.microsoft.azure.functions.worker.broker;

import org.junit.*;

import static junit.framework.TestCase.*;

import java.io.File;
import java.util.*;


import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;

public class JavaMethodExecutorTest {

	private FunctionClassLoaderProvider functionClassLoaderProvider;

	@Before
	public void setClassLoaderProvider() throws Exception {
		functionClassLoaderProvider = new FunctionClassLoaderProvider();
		String baseDir = System.getProperty("user.dir");
		File surefireTestJar = new File(baseDir + "/test-classes/TestFunctionsClass.jar");
		File testJar = new File(baseDir + "/target/test-classes/TestFunctionsClass.jar");
		functionClassLoaderProvider.addCustomerUrl(testJar.toURI().toURL());
		functionClassLoaderProvider.addCustomerUrl(surefireTestJar.toURI().toURL());
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
    public void functionMethod_doesnotexist_LoadFails() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }
	
	@Test(expected = UnsupportedOperationException.class)   
    public void functionMethod_DuplicateAnnotations_LoadFails() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FunctionMethodExecutorImpl(descriptor, bindings, functionClassLoaderProvider);
    }

	@Test
	public void test(){
		ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
		System.out.println(1);
	}
}
