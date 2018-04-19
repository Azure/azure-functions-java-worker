package com.microsoft.azure.webjobs.script.broker.tests;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.serverless.functions.OutputBinding;
import com.microsoft.azure.webjobs.script.test.categories.*;
import org.junit.*;
import org.junit.experimental.categories.*;

import static com.microsoft.azure.webjobs.script.broker.CoreTypeResolver.getRuntimeClass;
import static com.microsoft.azure.webjobs.script.broker.CoreTypeResolver.isValidOutputType;
import static org.junit.Assert.*;

public class CoreTypeResolverTests {
    @Test
    @Category({UnitTesting.class, SmokeTesting.class, FunctionalTesting.class})
    public void testIsValidOutputType() throws Exception {
        assertFalse(isValidOutputType(Integer.class));
        assertFalse(isValidOutputType(int.class));
        assertTrue(isValidOutputType(returnTypeOf("outputInteger")));
        assertFalse(isValidOutputType(returnTypeOf("outputOutputInteger")));
        assertTrue(isValidOutputType(ExtendedOutputBinding.class));
        assertFalse(isValidOutputType(returnTypeOf("listInteger")));
        assertTrue(isValidOutputType(returnTypeOf("outputListInteger")));
        assertTrue(isValidOutputType(returnTypeOf("exoutputInteger")));
        assertTrue(isValidOutputType(returnTypeOf("exoutputListInteger")));
        assertFalse(isValidOutputType(returnTypeOf("exoutputOutputInteger")));
        assertFalse(isValidOutputType(returnTypeOf("outputExoutputInteger")));
    }

    @Test
    @Category({UnitTesting.class, SmokeTesting.class, FunctionalTesting.class})
    public void testGetRuntimeClass() throws Exception {
        assertEquals(Integer.class, getRuntimeClass(Integer.class));
        assertEquals(Integer.TYPE, getRuntimeClass(int.class));
        assertEquals(OutputBinding.class, getRuntimeClass(returnTypeOf("outputInteger")));
        assertEquals(OutputBinding.class, getRuntimeClass(returnTypeOf("outputOutputInteger")));
        assertEquals(List.class, getRuntimeClass(returnTypeOf("listInteger")));
        assertEquals(OutputBinding.class, getRuntimeClass(returnTypeOf("outputListInteger")));
        assertEquals(GenericExtendedOutputBinding.class, getRuntimeClass(returnTypeOf("exoutputOutputInteger")));
        assertEquals(OutputBinding.class, getRuntimeClass(returnTypeOf("outputExoutputInteger")));
    }

    private OutputBinding<Integer> outputInteger() { return null; }
    private OutputBinding<OutputBinding<Integer>> outputOutputInteger() { return null; }
    private List<Integer> listInteger() { return null; }
    private OutputBinding<List<Integer>> outputListInteger() { return null; }
    private GenericExtendedOutputBinding<Integer> exoutputInteger() { return null; }
    private GenericExtendedOutputBinding<List<Integer>> exoutputListInteger() { return null; }
    private GenericExtendedOutputBinding<OutputBinding<Integer>> exoutputOutputInteger() { return null; }
    private OutputBinding<GenericExtendedOutputBinding<Integer>> outputExoutputInteger() { return null; }
    private Type returnTypeOf(String type) throws NoSuchMethodException {
        Method m = CoreTypeResolverTests.class.getDeclaredMethod(type);
        m.setAccessible(true);
        return m.getGenericReturnType();
    }

    private class ExtendedOutputBinding implements OutputBinding<Integer> {
        @Override
        public Integer getValue() { return null; }
        @Override
        public void setValue(Integer value) {}
    }
    private class GenericExtendedOutputBinding<T> implements OutputBinding<T> {
        @Override
        public T getValue() { return null; }
        @Override
        public void setValue(T value) {}
    }
}
