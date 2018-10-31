package com.microsoft.azure.functions.worker.binding.tests;

import static org.junit.Assert.assertEquals;

import java.lang.invoke.WrongMethodTypeException;
import java.util.Optional;

import org.junit.Test;

import com.microsoft.azure.functions.worker.binding.BindingData;
import com.microsoft.azure.functions.worker.binding.RpcIntegerDataSource;

public class RpcIntegerDataSourceTests {

	@Test
	public void rpcIntegerDataSource_To_Integer() {
		String sourceKey = "testInt";
		Integer input = 1234;
		RpcIntegerDataSource stringData = new RpcIntegerDataSource(sourceKey, input);
		BindingData bindingData = new BindingData(input);

		Optional<BindingData> actualBindingData = stringData.computeByName(sourceKey, Integer.class);
		BindingData actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		assertEquals(bindingData.getValue(), actualArg.getValue());

		actualBindingData = stringData.computeByName(sourceKey, Short.class);
		actualArg = actualBindingData.orElseThrow(WrongMethodTypeException::new);
		bindingData = new BindingData(input.shortValue());
		assertEquals((Short) bindingData.getValue(), actualArg.getValue());
	}
}
