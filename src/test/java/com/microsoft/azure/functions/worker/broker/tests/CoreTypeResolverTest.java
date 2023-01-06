package com.microsoft.azure.functions.worker.broker.tests;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.worker.broker.CoreTypeResolver;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static com.microsoft.azure.functions.worker.broker.CoreTypeResolver.getRuntimeClass;
import static com.microsoft.azure.functions.worker.broker.CoreTypeResolver.isValidOutputType;
import static org.junit.jupiter.api.Assertions.*;

public class CoreTypeResolverTest {

	public void CustomBinding_Valid(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET,
					HttpMethod.POST }, authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<String> request,
			@TestCustomBindingNoName(index = "testIndex", path = "testPath") String customInput) {
	}

	public void TestCustomBindingName_OverridesCustomBindingName(
			@HttpTrigger(name = "req", methods = { HttpMethod.GET,
					HttpMethod.POST }, authLevel = AuthorizationLevel.FUNCTION) HttpRequestMessage<String> request,
			@TestCustomBinding(index = "testIndex", path = "testPath", name = "testMessage") String customInput) {
	}

	public void CustomBinding_Invalid(
			@InvalidCustomBinding(index = "testIndex", path = "testPath") String customInput) {
	}

	@Test
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

	@Test
	public void getNameFromCustomBinding() {
		Method customBindingValid = getFunctionMethod("CustomBinding_Valid");
		Parameter[] parameters = customBindingValid.getParameters();
		for (Parameter parameter : parameters) {
			String annotationName = CoreTypeResolver.getAnnotationName(parameter);
			assertNotNull(annotationName);
		}
	}

	@Test
	public void getNameFromTestCustomBinding() {
		Method customBindingValid = getFunctionMethod("TestCustomBindingName_OverridesCustomBindingName");
		Parameter[] parameters = customBindingValid.getParameters();
		for (Parameter parameter : parameters) {
			String annotationName = CoreTypeResolver.getAnnotationName(parameter);
			assertNotNull(annotationName);
			assertTrue(StringUtils.isNotEmpty(annotationName));
		}
	}

	@Test
	public void getNameFromCustomBinding_ReturnsNull() {
		Method customBindingInvalid = getFunctionMethod("CustomBinding_Invalid");
		Parameter[] parameters = customBindingInvalid.getParameters();
		for (Parameter parameter : parameters) {
			String annotationName = CoreTypeResolver.getAnnotationName(parameter);
			assertNull(annotationName);
		}
	}

	private OutputBinding<Integer> outputInteger() {
		return null;
	}

	private OutputBinding<OutputBinding<Integer>> outputOutputInteger() {
		return null;
	}

	private List<Integer> listInteger() {
		return null;
	}

	private OutputBinding<List<Integer>> outputListInteger() {
		return null;
	}

	private GenericExtendedOutputBinding<Integer> exoutputInteger() {
		return null;
	}

	private GenericExtendedOutputBinding<List<Integer>> exoutputListInteger() {
		return null;
	}

	private GenericExtendedOutputBinding<OutputBinding<Integer>> exoutputOutputInteger() {
		return null;
	}

	private OutputBinding<GenericExtendedOutputBinding<Integer>> outputExoutputInteger() {
		return null;
	}

	private Type returnTypeOf(String type) throws NoSuchMethodException {
		Method m = CoreTypeResolverTest.class.getDeclaredMethod(type);
		m.setAccessible(true);
		return m.getGenericReturnType();
	}

	private class ExtendedOutputBinding implements OutputBinding<Integer> {
		@Override
		public Integer getValue() {
			return null;
		}

		@Override
		public void setValue(Integer value) {
		}
	}

	private class GenericExtendedOutputBinding<T> implements OutputBinding<T> {
		@Override
		public T getValue() {
			return null;
		}

		@Override
		public void setValue(T value) {
		}
	}

	private Method getFunctionMethod(String methodName) {
		CoreTypeResolverTest coreTypeResolverTest = new CoreTypeResolverTest();
		Class<? extends CoreTypeResolverTest> testsClass = coreTypeResolverTest.getClass();
		Method[] methods = testsClass.getMethods();
		Method functionMethod = null;
		for (Method method : methods) {
			if (method.getName() == methodName) {
				functionMethod = method;
				break;
			}
		}
		return functionMethod;
	}
}
