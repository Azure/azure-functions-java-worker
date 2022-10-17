//package com.microsoft.azure.functions.worker.functional.tests;
//
//import com.microsoft.azure.functions.rpc.messages.*;
//import com.microsoft.azure.functions.worker.test.utilities.*;
//import org.junit.*;
//import org.junit.runner.RunWith;
//import org.junit.runners.Parameterized;
//import org.junit.runners.Parameterized.Parameters;
//import org.junit.runners.Parameterized.Parameter;
//import static org.junit.Assert.*;
//
//@RunWith(Parameterized.class)
//public class SimpleParamReturnTest extends FunctionsTestBase {
//
//	@Parameters
//	public static Object[] data() {
//		return new Object[] { "test String" };
//	}
//
//	@Parameter
//	public String stringInput;
//	private static String stringReturnValue;
//
//	public String ReturnStringFunction() {
//		return stringReturnValue;
//	}
//
//	@Test
//	public void testStringData() throws Exception {
//		stringReturnValue = stringInput;
//		System.setProperty("azure.functions.worker.java.skip.testing", "true");
//		try (FunctionsTestHost host = new FunctionsTestHost()) {
//			this.loadFunction(host, "returnStringTestId", "ReturnStringFunction");
//			InvocationResponse stringResponse = host.call("getret", "returnStringTestId");
//			assertEquals(TypedData.DataCase.STRING, stringResponse.getReturnValue().getDataCase());
//			assertEquals(stringInput, stringResponse.getReturnValue().getString());
//		}
//	}
//}
