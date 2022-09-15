package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class JavaFunctionBrokerTest {
    @Mock InvocationRequest request;
    @Mock ParameterBinding binding;
    @Mock TypedData bindingData;
    @Mock TypedData name;
    @Mock TypedData query;
    @Mock TypedData headers;
    @Mock TypedData sys;
    @Mock TypedData queueTrigger;
    @Mock TypedData dequeueCount;
    @Mock TypedData expirationTime;
    @Mock TypedData id;
    @Mock TypedData insertionTime;
    @Mock TypedData nextVisibleTime;
    @Mock TypedData popReceipt;

    @Test
    public void getTriggerMetadataMap_success() throws Exception {
        String expectedData = "http {\n  method: \"GET\"\n  url: \"https://localhost:5001/api/HttpExample?name=ushio\"\n  headers {\n    key: \"cache-control\"\n    value: \"max-age=0\"\n  }\n  headers {\n    key: \"connection\"\n    value: \"Keep-Alive\"\n  }\n  headers {\n    key: \"accept\"\n    value: \"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\"\n  }\n  headers {\n    key: \"accept-encoding\"\n    value: \"gzip, deflate, br\"\n  }\n  headers {\n    key: \"accept-language\"\n    value: \"en-US,ja;q=0.8,en-GB;q=0.5,en;q=0.3\"\n  }\n  headers {\n    key: \"host\"\n    value: \"localhost:5001\"\n  }\n  headers {\n    key: \"user-agent\"\n    value: \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.19041\"\n  }\n  headers {\n    key: \"upgrade-insecure-requests\"\n    value: \"1\"\n  }\n  query {\n    key: \"name\"\n    value: \"ushio\"\n  }\n  identities {\n    name_claim_type {\n      value: \"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name\"\n    }\n    role_claim_type {\n      value: \"http://schemas.microsoft.com/ws/2008/06/identity/claims/role\"\n    }\n  }\n}\n";
        String expectedName = "req";

        when(bindingData.hasHttp()).thenReturn(true);
        when(bindingData.getString()).thenReturn(expectedData);
        when(binding.getName()).thenReturn(expectedName);
        when(binding.getData()).thenReturn(bindingData);
        when(request.getInputDataList()).thenReturn(Arrays.asList(binding));

        lenient().when(name.getString()).thenReturn("string: \"John\"\n");
        lenient().when(query.getString()).thenReturn("json: \"{\"name\":\"ushio\"}\"");
        lenient().when(headers.getString()).thenReturn("json: \"{\"Cache-Control\":\"max-age=0\",\"Connection\":\"Keep-Alive\",\"Accept\":\"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\",\"Accept-Encoding\":\"gzip, deflate, br\",\"Accept-Language\":\"en-US,ja;q=0.8,en-GB;q=0.5,en;q=0.3\",\"Host\":\"localhost:5001\",\"User-Agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.19041\",\"Upgrade-Insecure-Requests\":\"1\"}\"");
        lenient().when(sys.getString()).thenReturn("json: \"{\"MethodName\":\"HttpExample\",\"UtcNow\":\"2020-04-30T15:26:57.281277Z\",\"RandGuid\":\"cd332c4a-df9e-415a-acd4-973994072e46\"}\"");

        Map<String,TypedData> triggerMetadata = new HashMap<String, TypedData>();
        triggerMetadata.put("name", name);
        triggerMetadata.put("Query", query);
        triggerMetadata.put("Headers", headers);
        triggerMetadata.put("sys", sys);
        when(request.getTriggerMetadataMap()).thenReturn(Collections.unmodifiableMap(triggerMetadata));

        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        Map<String, TypedData> actualTriggerMetadata = broker.getTriggerMetadataMap(request);
        TypedData actual = actualTriggerMetadata.get("$request");
        assertEquals(actual.getString(), expectedData);
        TypedData actual2 = actualTriggerMetadata.get(expectedName);
        assertEquals(actual2.getString(), expectedData);
    }

    @Test
    public void getTriggerMetadataMap_ignored() throws Exception {
        String data = "string: \"hello queue\"\n";
        String name = "msg";

        when(bindingData.hasHttp()).thenReturn(false);
        lenient().when(bindingData.getString()).thenReturn(data);
        lenient().when(binding.getName()).thenReturn(name);
        when(binding.getData()).thenReturn(bindingData);
        when(request.getInputDataList()).thenReturn(Arrays.asList(binding));

        lenient().when(queueTrigger.getString()).thenReturn("string: \"hello queue\"\n");
        lenient().when(dequeueCount.getString()).thenReturn("json: \"1\"\n");
        lenient().when(expirationTime.getString()).thenReturn("json: \"\"2020-05-08T17:47:22+00:00\"\n");
        lenient().when(id.getString()).thenReturn("string: \"e4f4a332-df80-41a1-8ecd-c2f7fba91f28\"\n");
        lenient().when(insertionTime.getString()).thenReturn("json: \"\"2020-05-01T17:47:22+00:00\"\n");
        lenient().when(nextVisibleTime.getString()).thenReturn("json: \"\"2020-05-01T17:57:22+00:00\"\n");
        lenient().when(popReceipt.getString()).thenReturn("string: \"oJCJGfnt1wgBAAAA\"\n");
        lenient().when(sys.getString()).thenReturn( "json: \"{\"MethodName\":\"QueueProcessor\",\"UtcNow\":\"2020-05-01T17:47:29.2664174Z\",\"RandGuid\":\"7f67ac1c-b7b0-43f5-a51a-73af321d7d9f\"}\n");

        Map<String,TypedData> triggerMetadata = new HashMap<String, TypedData>();
        triggerMetadata.put("QueueTrigger", queueTrigger);
        triggerMetadata.put("DequeueCount", dequeueCount);
        triggerMetadata.put("ExpirationTime", expirationTime);
        triggerMetadata.put("Id", id);
        triggerMetadata.put("InsertionTime", insertionTime);
        triggerMetadata.put("NextVisibleTime", nextVisibleTime);
        triggerMetadata.put("PopReceipt", popReceipt);
        triggerMetadata.put("sys", sys);
        when(request.getTriggerMetadataMap()).thenReturn(Collections.unmodifiableMap(triggerMetadata));

        int expectedCount = request.getTriggerMetadataMap().size();
        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        Map<String, TypedData> actualTriggerMetadata = broker.getTriggerMetadataMap(request);
        // In case of non-http request, it will not modify the triggerMetadata
        assertEquals(expectedCount, actualTriggerMetadata.size());
    }
}
