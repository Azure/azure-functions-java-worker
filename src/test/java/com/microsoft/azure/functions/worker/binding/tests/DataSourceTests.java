package com.microsoft.azure.functions.worker.binding.tests;

import java.lang.invoke.WrongMethodTypeException;
import java.util.*;

import org.junit.*;

import static org.junit.Assert.*;
import com.microsoft.azure.functions.worker.binding.*;

public class DataSourceTests {
	
	public static class TestPOJO {
        public String id;
        public String name;
        public String Description;
    }

	// @Test
	public void rpcStringDataSource_To_String() {
		String sourceKey = "testString";
		String inputString = "Test String";
		RpcStringDataSource stringData = new RpcStringDataSource(sourceKey, inputString);
		BindingData bindingData = new BindingData(inputString);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, String.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		assertEquals(bindingData.getValue(), actualArg.getValue());
	}

	// @Test
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
		String jsonInStringArray = "[{\"id\":7500 , \"name\":\"joe\"}, {\"id\":7502 , \"name\":\"joe\"}]";
		RpcJsonDataSource stringData = new RpcJsonDataSource(sourceKey, jsonInStringArray);
		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, TestPOJO.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		TestPOJO[] convertedData = (TestPOJO[]) actualArg.getValue();
		assertTrue(convertedData.length == 2);
		assertTrue(convertedData[0].contains("7500"));
		assertTrue(convertedData[1].contains("7502"));
	}
	
	

}
