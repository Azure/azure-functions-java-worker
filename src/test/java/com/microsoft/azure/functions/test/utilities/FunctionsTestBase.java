package com.microsoft.azure.functions.test.utilities;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.functions.rpc.messages.*;

public class FunctionsTestBase {
    protected void loadFunction(FunctionsTestHost host, String id, String method) throws Exception {
        String fullname = this.getClass().getCanonicalName() + "." + method;
        Map<String, BindingInfo> bindings = new HashMap<>();
        Method entry = this.getClass().getMethod(method);
        if (!entry.getReturnType().equals(Void.TYPE)) {
            bindings.put("$return", BindingInfo.newBuilder().setDirection(BindingInfo.Direction.out).build());
        }
        host.loadFunction(id, fullname, bindings);
    }
}
