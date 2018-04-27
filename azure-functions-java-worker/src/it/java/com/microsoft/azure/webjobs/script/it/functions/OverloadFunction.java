package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.*;
import com.microsoft.azure.webjobs.script.it.functions.dto.*;

import java.util.Optional;

public class OverloadFunction {

    /**
     * overloaded function name, test case 1
     */
    @FunctionName("overloadMethodOne")
    public static String method(OverloadPojo1 obj) {
        return "Overload " + obj.number;
    }

    @FunctionName("overloadMethodTwo")
    public static String method(OverloadPojo2 obj) {
        return "Overload " + obj.number;
    }

    public static String bindingName(@HttpTrigger(name = "req") Optional<String> request) {
        return "This is correct method for HttpBinding resolution";
    }

    public static String bindingName(HttpRequestMessage<Optional<String>> request) {
        return "This method should not be called";
    }
}
