package com.microsoft.azure.functions.worker.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import com.microsoft.azure.functions.TraceContext;
import com.microsoft.azure.functions.worker.binding.ExecutionTraceContext;
import org.junit.jupiter.api.Test;


public class ExecutionTraceContextTest {

    @Test
    public void TraceContext_test_getAndset_nonEmpty() {
        String traceParent = "randomTraceParent";
        String traceState = "randomTraceState";
        HashMap<String, String> attributes = new HashMap<String, String>();
        HashMap<String, String> baggage = new HashMap<String, String>();

        attributes.put("key1", "value1");
        attributes.put("key2", "value2");
        baggage.put("baggageKey1", "baggageValue1");
        baggage.put("baggageKey2", "baggageValue2");

        ExecutionTraceContext testTraceContext = new ExecutionTraceContext(traceParent, traceState, attributes, baggage);
        assertEquals(traceParent, testTraceContext.getTraceparent());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(attributes, testTraceContext.getAttributes());
        assertEquals(baggage, testTraceContext.getBaggage());
    }

    @Test
    public void TraceContext_test_getAndset_Empty() {
        String traceParent = "";
        String traceState = "";
        HashMap<String, String> attributes = new HashMap<String, String>();
        HashMap<String, String> baggage = new HashMap<String, String>();
        ExecutionTraceContext testTraceContext = new ExecutionTraceContext(traceParent, traceState, attributes, baggage);
        assertEquals(traceParent, testTraceContext.getTraceparent());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(attributes, testTraceContext.getAttributes());
        assertEquals(baggage, testTraceContext.getBaggage());
    }

    @Test
    public void TraceContext_test_getAndset_Null() {
        ExecutionTraceContext testTraceContext = new ExecutionTraceContext(null, null, null, null);
        assertEquals(null, testTraceContext.getTraceparent());
        assertEquals(null, testTraceContext.getTracestate());
        assertEquals(null, testTraceContext.getTracestate());
        assertEquals(null, testTraceContext.getAttributes());
        assertEquals(null, testTraceContext.getBaggage());
    }
}
