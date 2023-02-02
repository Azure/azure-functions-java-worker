package com.microsoft.azure.functions.worker.broker.tests;

import java.util.*;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.broker.*;
import com.microsoft.azure.functions.worker.description.*;
import com.microsoft.azure.functions.worker.reflect.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;


public class FunctionDefinitionTest {

    @Test
    public void functionMethodLoadSucceeds_8() throws Exception {
        FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger", "TestFunctionsClass.jar", false);
        Map<String, BindingInfo> bindings = new HashMap<>();
        bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
        FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        assertNotNull(functionDefinition.getCandidate());
    }

    @Test
    public void functionMethod_doesnotexist_LoadFails_8() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> {
            FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1", "TestFunctionsClass.jar", false);
            Map<String, BindingInfo> bindings = new HashMap<>();
            bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
            FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        });
    }

    @Test
    public void functionMethod_DuplicateAnnotations_LoadFails_8() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload", "TestFunctionsClass.jar", false);
            Map<String, BindingInfo> bindings = new HashMap<>();
            bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
            FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        });
    }

    @Test
    public void functionMethodLoadSucceeds_11() throws Exception {
        FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger", "TestFunctionsClass.jar", false);
        Map<String, BindingInfo> bindings = new HashMap<>();
        bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
        System.setProperty("java.specification.version", "11");
        FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        assertNotNull(functionDefinition.getCandidate());
    }

    @Test
    public void functionMethod_doesnotexist_LoadFails_11() throws Exception {
        assertThrows(NoSuchMethodException.class, () -> {
            System.setProperty("java.specification.version", "11");
            FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTrigger1", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTrigger1", "TestFunctionsClass.jar", false);
            Map<String, BindingInfo> bindings = new HashMap<>();
            bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
            FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        });
    }

    @Test
    public void functionMethod_DuplicateAnnotations_LoadFails_11() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> {
            System.setProperty("java.specification.version", "11");
            FunctionMethodDescriptor descriptor = new FunctionMethodDescriptor("testid", "TestHttpTriggerOverload", "com.microsoft.azure.functions.worker.broker.tests.TestFunctionsClass.TestHttpTriggerOverload", "TestFunctionsClass.jar", false);
            Map<String, BindingInfo> bindings = new HashMap<>();
            bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
            FunctionDefinition functionDefinition = new FunctionDefinition(descriptor, bindings, new FactoryClassLoader().createClassLoaderProvider());
        });
    }
}
