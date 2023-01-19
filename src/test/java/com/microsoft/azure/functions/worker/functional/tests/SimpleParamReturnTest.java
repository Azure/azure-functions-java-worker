package com.microsoft.azure.functions.worker.functional.tests;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.worker.test.utilities.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SimpleParamReturnTest extends FunctionsTestBase {
	private static String stringReturnValue;

	public String ReturnStringFunction() {
		return stringReturnValue;
	}

	@ParameterizedTest
	@ValueSource(strings = {"test String"})
	public void testStringData(String stringInput) throws Exception {
		stringReturnValue = stringInput;
		System.setProperty("azure.functions.worker.java.skip.testing", "true");
		try (FunctionsTestHost host = new FunctionsTestHost()) {
			this.loadFunction(host, "returnStringTestId", "ReturnStringFunction");
			InvocationResponse stringResponse = host.call("getret", "returnStringTestId");
			assertEquals(TypedData.DataCase.STRING, stringResponse.getReturnValue().getDataCase());
			assertEquals(stringInput, stringResponse.getReturnValue().getString());
		}
	}
}
