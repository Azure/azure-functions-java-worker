package com.microsoft.azure.functions.worker.functional.tests;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.worker.test.utilities.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.runners.Parameterized.Parameter;
import static org.junit.Assert.*;

@RunWith(Parameterized.class)
public class SimpleParamReturnTests extends FunctionsTestBase {
		
	@Parameters
	public static Object[] data() {
	    return new Object[] { "test String", "" };
	}
	
	@Parameter 
    public String stringInput;

	private static int intReturnValue = 124;
	private static String stringReturnValue;
	
    @FunctionName("ReturnStringTest")
    public String ReturnStringFunction() {
		
		return stringReturnValue;
	}
	
    @FunctionName("ReturnIntTest")
	public static int ReturnIntFunction() {
		return intReturnValue;
	}
	
	@Test
    public void testStringData()  throws Exception {
		System.out.println("Run Test {}..."+ stringInput);
		stringReturnValue = stringInput;
		try (FunctionsTestHost host = new FunctionsTestHost()) {
			this.loadFunction(host, "returnStringTestId", "ReturnStringFunction");
			InvocationResponse response = host.call("getret", "returnStringTestId");			
            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            String receivedString = response.getReturnValue().getString();			
			assertEquals(stringInput, receivedString);
		}
	}
    

	@Test	
	public void testIntReturn() throws Exception {
		try (FunctionsTestHost host = new FunctionsTestHost()) {
			this.loadFunction(host, "intreturn", "ReturnIntFunction");			
			InvocationResponse response = host.call("getret13579", "intreturn");
			assertEquals(TypedData.DataCase.INT, response.getReturnValue().getDataCase());
			assertEquals(intReturnValue, response.getReturnValue().getInt());
		}
	}
}
