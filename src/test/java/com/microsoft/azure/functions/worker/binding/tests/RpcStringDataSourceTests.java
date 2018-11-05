package com.microsoft.azure.functions.worker.binding.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.Test;

import com.google.gson.JsonSyntaxException;
import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcJsonDataSource;
import com.microsoft.azure.functions.worker.binding.RpcStringDataSource;

public class RpcStringDataSourceTests {

	public static class TestPOJO {
		public Integer id;
		public String name;
		public String Description;
	}

	public void FunctionWithPOJOListInput(ArrayList<TestPOJO> items) {
	}

	@Test
	public void rpcStringDataSource_To_String() {
		String sourceKey = "testString";
		String inputString = "Test String";
		RpcStringDataSource stringData = new RpcStringDataSource(sourceKey, inputString);
		BindingData bindingData = new BindingData(inputString);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		assertEquals(bindingData.getValue(), actualArg.getValue());
	}

	@Test
	public void rpcJsonStringDataSource_To_String() {
		String sourceKey = "testStringJson";
		String jsonInString = "{\"id\":7500 , \"testname\":\"joe\"}";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInString);
		BindingData bindingData = new BindingData(jsonInString);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		assertEquals(bindingData.getValue(), actualArg.getValue());
	}

	@Test
	public void rpcStringArrayDataSource_To_StringArray() {
		String sourceKey = "testStringArray";
		String jsonInStringArray = "[\"item1\", \"item2\"]";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInStringArray);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String[].class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		String[] convertedData = (String[]) actualArg.getValue();
		assertTrue(convertedData.length == 2);
		assertTrue(convertedData[0].contains("item1"));
		assertTrue(convertedData[1].contains("item2"));
	}

	@Test
	public void rpcJsonStringArrayDataSource_To_POJOArray() {
		String sourceKey = "testStringJsonArray";
		String jsonInStringArray = "[{\"id\":7500, \"name\":\"joe\"}, {\"id\":7501 , \"name\":\"joe\"}]";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInStringArray);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, TestPOJO[].class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		TestPOJO[] convertedData = (TestPOJO[]) actualArg.getValue();
		assertTrue(convertedData.length == 2);
		assertTrue(convertedData[0].id == 7500);
		assertEquals(convertedData[0].name, "joe");
		assertTrue(convertedData[1].id == 7501);
		assertEquals(convertedData[1].name, "joe");
	}
	
	@Test
	public void rpcJsonStringDataSource_To_POJO() {
		String sourceKey = "testStringJson";
		String jsonInStringArray = "{\"id\":7500, \"name\":\"joe\"}";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInStringArray);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, TestPOJO.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		TestPOJO convertedData = (TestPOJO) actualArg.getValue();
		assertTrue(convertedData.id == 7500);
		assertEquals(convertedData.name, "joe");
	}

	@Test
	public void rpcJsonStringArrayDataSource_To_POJOList() throws NoSuchMethodException, SecurityException {

		String sourceKey = "testStringJsonArray";
		String jsonInStringArray = "[{\"id\":7500, \"name\":\"joe\"}, {\"id\":7501 , \"name\":\"joe\"}]";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInStringArray);

		RpcStringDataSourceTests stringDataSourceTests = new RpcStringDataSourceTests();
		Class<? extends RpcStringDataSourceTests> stringDataSourceTestsClass = stringDataSourceTests.getClass();
		Method[] methods = stringDataSourceTestsClass.getMethods();
		Method functionWithPOJOListInputMethod = null;
		for (Method method : methods) {
			if (method.getName() == "FunctionWithPOJOListInput") {
				functionWithPOJOListInputMethod = method;
				break;
			}
		}

		Parameter[] parameters = functionWithPOJOListInputMethod.getParameters();

		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey,
				parameters[0].getParameterizedType());
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		List<TestPOJO> convertedData = (List<TestPOJO>) actualArg.getValue();
		assertTrue(convertedData.size() == 2);
		assertTrue(convertedData.get(0).id == 7500);
		assertEquals(convertedData.get(0).name, "joe");
		assertTrue(convertedData.get(1).id == 7501);
		assertEquals(convertedData.get(1).name, "joe");
	}
	
	@Test(expected = JsonSyntaxException.class) 
	public void rpcStringStringDataSource_To_POJO_Throws() {
		String sourceKey = "testString";
		String testString = "item1";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, testString);
		stringData.computeByName(sourceKey, TestPOJO.class);
	}
}
