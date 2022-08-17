package com.microsoft.azure.functions.worker.broker.tests;

import org.junit.*;

import static junit.framework.TestCase.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;

public class JavaMethodExecutorTests {
	@Test
    public void functionMethodLoadSucceeds_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.setProperty("java.specification.version", "1.8");
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
    }
	
	@Test(expected = NoSuchMethodException.class)   
    public void functionMethod_doesnotexist_LoadFails_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		
    }
	
	@Test(expected = UnsupportedOperationException.class)   
    public void functionMethod_DuplicateAnnotations_LoadFails_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
    }

	@Test
	public void functionMethodLoadSucceeds_11() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.setProperty("java.specification.version", "11");
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
	}

	@Test(expected = NoSuchMethodException.class)
	public void functionMethod_doesnotexist_LoadFails_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());

	}

	@Test(expected = UnsupportedOperationException.class)
	public void functionMethod_DuplicateAnnotations_LoadFails_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
	}

	// used to override function factory path
	static class FunctionFactoryClassLoader extends ClassLoader {
		private final String metaInfLocation;

		public FunctionFactoryClassLoader(ClassLoader parent, String location) {
			super(parent);
			this.metaInfLocation = location;
		}

		@Override
		public InputStream getResourceAsStream(String name) {
			if (AbstractJavaMethodExecutor.SERVICE_META_INF_PATH.equals(name)) name = metaInfLocation;
			return super.getResourceAsStream(name);
		}
	}
	@Test
	public void functionFunctionFactorySucceeds_8() throws Exception {
		System.setProperty("java.specification.version", "1.8");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "functionFactory.txt");
		testFunctionFactory(cl);
	}

	@Test(expected = NoSuchMethodException.class)
	public void functionFunctionFactoryBadMethod_8() throws Exception {
		System.setProperty("java.specification.version", "1.8");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "badMethodFunctionFactory.txt");
		testFunctionFactory(cl);
	}

	@Test(expected = ClassNotFoundException.class)
	public void functionFunctionFactoryNotFound_8() throws Exception {
		System.setProperty("java.specification.version", "1.8");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "notFoundFunctionFactory.txt");
		testFunctionFactory(cl);
	}

	@Test
	public void functionFunctionFactorySucceeds_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "functionFactory.txt");
		testFunctionFactory(cl);
	}

	@Test(expected = NoSuchMethodException.class)
	public void functionFunctionFactoryBadMethod_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "badMethodFunctionFactory.txt");
		testFunctionFactory(cl);
	}

	@Test(expected = ClassNotFoundException.class)
	public void functionFunctionFactoryNotFound_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionFactoryClassLoader cl = new FunctionFactoryClassLoader(JavaMethodExecutorTests.class.getClassLoader(), "notFoundFunctionFactory.txt");
		testFunctionFactory(cl);
	}

	private void testFunctionFactory(FunctionFactoryClassLoader cl) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		JavaMethodExecutor executor = new FactoryJavaMethodExecutor().getJavaMethodExecutor(descriptor, bindings, new ClassLoaderProvider() {
			@Override
			public void addCustomerUrl(URL url) throws IOException {

			}

			@Override
			public void addWorkerUrl(URL url) throws IOException {

			}

			@Override
			public ClassLoader createClassLoader() {
				return cl;
			}
		});
		assertTrue(executor.hasFactory());
		assertTrue(executor.getOverloadResolver().hasCandidates());
		assertFalse(executor.getOverloadResolver().hasMultipleCandidates());
	}
}
