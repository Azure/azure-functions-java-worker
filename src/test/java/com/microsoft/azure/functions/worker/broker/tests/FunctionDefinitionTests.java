package com.microsoft.azure.functions.worker.broker.tests;

import org.junit.*;
import java.util.*;
import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import static junit.framework.TestCase.*;


public class FunctionDefinitionTests {
	
	@Test    
    public void functionMethodLoadSucceeds_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		assertNotNull(functionDefinition.getCandidate());
    }
	
	@Test(expected = NoSuchMethodException.class)   
    public void functionMethod_doesnotexist_LoadFails_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		
    }
	
	@Test(expected = UnsupportedOperationException.class)   
    public void functionMethod_DuplicateAnnotations_LoadFails_8() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
    }

	@Test
	public void functionMethodLoadSucceeds_11() throws Exception {
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		System.setProperty("java.specification.version", "11");
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
		assertNotNull(functionDefinition.getCandidate());
	}

	@Test(expected = NoSuchMethodException.class)
	public void functionMethod_doesnotexist_LoadFails_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());

	}

	@Test(expected = UnsupportedOperationException.class)
	public void functionMethod_DuplicateAnnotations_LoadFails_11() throws Exception {
		System.setProperty("java.specification.version", "11");
		FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload","com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload","TestFunctionsClass.jar");
		Map<String, BindingInfo> bindings = new HashMap<>();
		bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
		FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
	}
}
