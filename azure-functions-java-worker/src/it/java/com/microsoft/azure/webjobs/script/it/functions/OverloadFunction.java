package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.HttpRequestMessage;
import com.microsoft.azure.serverless.functions.annotation.FunctionName;
import com.microsoft.azure.serverless.functions.annotation.HttpTrigger;
import java.util.Optional;

public class OverloadFunction {
    @FunctionName("ovldfunnametest1")
    public static String testExcludeFuncName(OverloadPojo1 unused) {
        return "This is the OverloadFunction Function Name Test Case 1";
    }

    @FunctionName("ovldfunnametest2")
    public static String testExcludeFuncName(OverloadPojo2 unused) {
        return "This is the OverloadFunction Function Name Test Case 2";
    }

    public static String testHttpBindingName(@HttpTrigger(name = "req") Optional<String> request) {
        return "This is correct method for HttpBinding resolution";
    }

    public static String testHttpBindingName(HttpRequestMessage<Optional<String>> request) {
        return "This method should not be called";
    }

    private static class OverloadPojo1 {
        private int x;
    }

    private static class OverloadPojo2 {
        private int x;
    }
}
