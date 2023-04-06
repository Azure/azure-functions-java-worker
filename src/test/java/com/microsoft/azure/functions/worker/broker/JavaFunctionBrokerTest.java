package com.microsoft.azure.functions.worker.broker;

import com.microsoft.azure.functions.rpc.messages.InvocationRequest;
import com.microsoft.azure.functions.rpc.messages.ParameterBinding;
import com.microsoft.azure.functions.rpc.messages.TypedData;
import com.microsoft.azure.functions.worker.reflect.DefaultClassLoaderProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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
        String reqValue = "http {\n  method: \"GET\"\n  url: \"https://localhost:5001/api/HttpExample?name=ushio\"\n  headers {\n    key: \"cache-control\"\n    value: \"max-age=0\"\n  }\n  headers {\n    key: \"connection\"\n    value: \"Keep-Alive\"\n  }\n  headers {\n    key: \"accept\"\n    value: \"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\"\n  }\n  headers {\n    key: \"accept-encoding\"\n    value: \"gzip, deflate, br\"\n  }\n  headers {\n    key: \"accept-language\"\n    value: \"en-US,ja;q=0.8,en-GB;q=0.5,en;q=0.3\"\n  }\n  headers {\n    key: \"host\"\n    value: \"localhost:5001\"\n  }\n  headers {\n    key: \"user-agent\"\n    value: \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.19041\"\n  }\n  headers {\n    key: \"upgrade-insecure-requests\"\n    value: \"1\"\n  }\n  query {\n    key: \"name\"\n    value: \"ushio\"\n  }\n  identities {\n    name_claim_type {\n      value: \"http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name\"\n    }\n    role_claim_type {\n      value: \"http://schemas.microsoft.com/ws/2008/06/identity/claims/role\"\n    }\n  }\n}\n";
        String reqName = "req";
        String nameValue = "string: \"John\"\n";
        String queryValue = "json: \"{\"name\":\"ushio\"}\"";
        String headersValue = "json: \"{\"Cache-Control\":\"max-age=0\",\"Connection\":\"Keep-Alive\",\"Accept\":\"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8\",\"Accept-Encoding\":\"gzip, deflate, br\",\"Accept-Language\":\"en-US,ja;q=0.8,en-GB;q=0.5,en;q=0.3\",\"Host\":\"localhost:5001\",\"User-Agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.102 Safari/537.36 Edge/18.19041\",\"Upgrade-Insecure-Requests\":\"1\"}\"";
        String sysValue = "json: \"{\"MethodName\":\"HttpExample\",\"UtcNow\":\"2020-04-30T15:26:57.281277Z\",\"RandGuid\":\"cd332c4a-df9e-415a-acd4-973994072e46\"}\"";


        when(bindingData.hasHttp()).thenReturn(true);
        when(bindingData.getString()).thenReturn(reqValue);
        when(binding.getName()).thenReturn(reqName);
        when(binding.getData()).thenReturn(bindingData);
        when(request.getInputDataList()).thenReturn(Arrays.asList(binding));

        when(name.getString()).thenReturn(nameValue);
        when(query.getString()).thenReturn(queryValue);
        when(headers.getString()).thenReturn(headersValue);
        when(sys.getString()).thenReturn(sysValue);

        Map<String,TypedData> triggerMetadata = new HashMap<String, TypedData>();
        triggerMetadata.put("name", name);
        triggerMetadata.put("Query", query);
        triggerMetadata.put("Headers", headers);
        triggerMetadata.put("sys", sys);
        when(request.getTriggerMetadataMap()).thenReturn(Collections.unmodifiableMap(triggerMetadata));

        JavaFunctionBroker broker = new JavaFunctionBroker(new DefaultClassLoaderProvider());
        Map<String, TypedData> actualTriggerMetadata = broker.getTriggerMetadataMap(request);
        assertEquals(reqValue, actualTriggerMetadata.get("$request").getString());
        assertEquals(reqValue, actualTriggerMetadata.get(reqName).getString());
        assertEquals(nameValue, actualTriggerMetadata.get("name").getString());
        assertEquals(queryValue, actualTriggerMetadata.get("Query").getString());
        assertEquals(headersValue, actualTriggerMetadata.get("Headers").getString());
        assertEquals(sysValue, actualTriggerMetadata.get("sys").getString());
    }

    @Test
    public void getTriggerMetadataMap_ignored() throws Exception {
        String msgValue = "string: \"hello queue\"\n";
        String msgName = "msg";
        String queueTriggerValue = "string: \"hello queue\"\n";
        String dequeueCountValue = "json: \"1\"\n";
        String expirationTimeValue = "json: \"\"2020-05-08T17:47:22+00:00\"\n";
        String idValue = "string: \"e4f4a332-df80-41a1-8ecd-c2f7fba91f28\"\n";
        String insertionTimeValue = "json: \"\"2020-05-01T17:47:22+00:00\"\n";
        String nextVisibleTimeValue = "json: \"\"2020-05-01T17:57:22+00:00\"\n";
        String popReceiptValue = "string: \"oJCJGfnt1wgBAAAA\"\n";
        String sysValue = "json: \"{\"MethodName\":\"QueueProcessor\",\"UtcNow\":\"2020-05-01T17:47:29.2664174Z\",\"RandGuid\":\"7f67ac1c-b7b0-43f5-a51a-73af321d7d9f\"}\n";


        when(bindingData.hasHttp()).thenReturn(false);
        when(binding.getData()).thenReturn(bindingData);
        when(request.getInputDataList()).thenReturn(Arrays.asList(binding));

        when(queueTrigger.getString()).thenReturn(queueTriggerValue);
        when(dequeueCount.getString()).thenReturn(dequeueCountValue);
        when(expirationTime.getString()).thenReturn(expirationTimeValue);
        when(id.getString()).thenReturn(idValue);
        when(insertionTime.getString()).thenReturn(insertionTimeValue);
        when(nextVisibleTime.getString()).thenReturn(nextVisibleTimeValue);
        when(popReceipt.getString()).thenReturn(popReceiptValue);
        when(sys.getString()).thenReturn(sysValue);

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
        assertNull(actualTriggerMetadata.get(msgName));
        assertEquals(queueTriggerValue, actualTriggerMetadata.get("QueueTrigger").getString());
        assertEquals(dequeueCountValue, actualTriggerMetadata.get("DequeueCount").getString());
        assertEquals(expirationTimeValue, actualTriggerMetadata.get("ExpirationTime").getString());
        assertEquals(idValue, actualTriggerMetadata.get("Id").getString());
        assertEquals(insertionTimeValue, actualTriggerMetadata.get("InsertionTime").getString());
        assertEquals(nextVisibleTimeValue, actualTriggerMetadata.get("NextVisibleTime").getString());
        assertEquals(popReceiptValue, actualTriggerMetadata.get("PopReceipt").getString());
        assertEquals(sysValue, actualTriggerMetadata.get("sys").getString());
    }
}
