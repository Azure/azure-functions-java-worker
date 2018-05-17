package com.microsoft.azure.functions.functional.tests;

import java.util.*;

import com.microsoft.azure.functions.rpc.messages.*;
import com.microsoft.azure.functions.test.categories.*;
import com.microsoft.azure.functions.test.utilities.*;
import org.junit.*;
import org.junit.experimental.categories.*;

import static org.junit.Assert.*;

public class SimpleParamReturnTests extends FunctionsTestBase {
    public static String EmptyParameterFunction() {
        return "Empty Parameter Result";
    }

    private static int intReturnValue = 0;
    public static int ReturnIntFunction() {
        return intReturnValue;
    }

    @Test
    @Category({IntegrationTesting.class, SmokeTesting.class, FunctionalTesting.class})
    public void testEmptyParameter() throws Exception {
        try (FunctionsTestHost host = new FunctionsTestHost()) {
            this.loadFunction(host, "emptyparam", "EmptyParameterFunction");
            InvocationResponse response = host.call("getret", "emptyparam");
            assertEquals(TypedData.DataCase.STRING, response.getReturnValue().getDataCase());
            assertEquals("Empty Parameter Result", response.getReturnValue().getString());
        }
    }

    @Test
    @Category({IntegrationTesting.class, SmokeTesting.class, FunctionalTesting.class})
    public void testIntReturn() throws Exception {
        try (FunctionsTestHost host = new FunctionsTestHost()) {
            this.loadFunction(host, "intreturn", "ReturnIntFunction");

            intReturnValue = 13579;
            InvocationResponse response = host.call("getret13579", "intreturn");
            assertEquals(TypedData.DataCase.INT, response.getReturnValue().getDataCase());
            assertEquals(intReturnValue, response.getReturnValue().getInt());

            intReturnValue = 24680;
            response = host.call("getret24680", "intreturn");
            assertEquals(TypedData.DataCase.INT, response.getReturnValue().getDataCase());
            assertEquals(intReturnValue, response.getReturnValue().getInt());

            for (int i = 0; i < 100; i++) {
                intReturnValue = new Random().nextInt();
                response = host.call("getretloop" + intReturnValue, "intreturn");
                assertEquals(TypedData.DataCase.INT, response.getReturnValue().getDataCase());
                assertEquals(intReturnValue, response.getReturnValue().getInt());
            }
        }
    }
}
