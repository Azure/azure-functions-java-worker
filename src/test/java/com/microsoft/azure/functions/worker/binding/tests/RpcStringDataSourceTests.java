package com.microsoft.azure.functions.worker.binding.tests;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.*;
import java.util.*;

import org.apache.commons.lang3.reflect.TypeUtils;
import org.junit.*;

import static org.junit.Assert.*;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.HttpStatus;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.CosmosDBInput;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.azure.functions.worker.binding.*;
import com.microsoft.azure.functions.worker.broker.CoreTypeResolver;

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
	public void rpcJsonStringArrayDataSource_To_POJO() {
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

}
