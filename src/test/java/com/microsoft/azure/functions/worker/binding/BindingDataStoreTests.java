package com.microsoft.azure.functions.worker.binding;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.microsoft.azure.functions.worker.binding.BindingDataStore;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.binding.DataSource;

import static org.junit.Assert.assertEquals;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Test;


import java.util.HashMap;
import java.util.Map;

public class BindingDataStoreTests {

    @Test
    public void validateStringInput(
            @Mocked TypedData data
    ) throws Exception {

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("content-type", "application/json");
        String inputString = "test";

        new Expectations() {{
            data.getString(); result = inputString;
            data.getDataCase(); result = TypedData.DataCase.STRING;
        }};
        DataSource actualValue =  BindingDataStore.deriveHttpBody(data, headerMap);
        assertEquals(inputString, actualValue.getValue());
    }

    @Test
    public void validateJsonInput(
            @Mocked TypedData data
    ) throws Exception {

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("content-type", "application/json");
        String inputString = "{ \"test\": \"value\" }";
        String expected = "{\"test\":\"value\"}";

        new Expectations() {{
            data.getString(); result = inputString;
            data.getDataCase(); result = TypedData.DataCase.STRING;
        }};
        DataSource actualValue =  BindingDataStore.deriveHttpBody(data, headerMap);
        assertEquals(expected, actualValue.getValue());
    }

    @Test
    public void inValidateJsonInput(
            @Mocked TypedData data
    ) throws Exception {

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("content-type", "application/json");
        String inputString = "{ \"test\": \"value\" ";
        String expected = "{ \"test\": \"value\" ";

        new Expectations() {{
            data.getString(); result = inputString;
            data.getDataCase(); result = TypedData.DataCase.STRING;
        }};
        DataSource actualValue =  BindingDataStore.deriveHttpBody(data, headerMap);
        assertEquals(expected, actualValue.getValue());
    }

    @Test
    public void validateJsonInputNotString(
            @Mocked TypedData data
    ) throws Exception {

        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("content-type", "application/json");
        String inputString = "{ \"test\": \"value\" }";
        String expected = "{ \"test\": \"value\" }";

        new Expectations() {{
            data.getJson(); result = inputString;
            data.getDataCase(); result = TypedData.DataCase.JSON;
        }};
        DataSource actualValue =  BindingDataStore.deriveHttpBody(data, headerMap);
        assertEquals(expected, actualValue.getValue());
    }
}