package com.microsoft.azure.functions.worker.binding;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import com.microsoft.azure.functions.TraceContext;

import org.junit.Test;

public class ExecutionTraceContextTest {

    @Test
    public void TraceContext_test_getAndset_nonEmpty() {
        String traceParent = "randomTraceParent";
        String traceState = "randomTraceState";
        HashMap<String, String> attributes = new HashMap<String, String>();

        attributes.put("key1", "value1");
        attributes.put("key2", "value2");

        TraceContext testTraceContext = new ExecutionTraceContext(traceParent, traceState, attributes);
        assertEquals(traceParent, testTraceContext.getTraceparent());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(attributes, testTraceContext.getAttributes());
    }

    @Test
    public void TraceContext_test_getAndset_Empty() {
        String traceParent = "";
        String traceState = "";
        HashMap<String, String> attributes = new HashMap<String, String>();
        TraceContext testTraceContext = new ExecutionTraceContext(traceParent, traceState, attributes);
        assertEquals(traceParent, testTraceContext.getTraceparent());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(traceState, testTraceContext.getTracestate());
        assertEquals(attributes, testTraceContext.getAttributes());
    }

    @Test
    public void TraceContext_test_getAndset_Null() {
        TraceContext testTraceContext = new ExecutionTraceContext(null, null, null);
        assertEquals(null, testTraceContext.getTraceparent());
        assertEquals(null, testTraceContext.getTracestate());
        assertEquals(null, testTraceContext.getTracestate());
        assertEquals(null, testTraceContext.getAttributes());
    }
}