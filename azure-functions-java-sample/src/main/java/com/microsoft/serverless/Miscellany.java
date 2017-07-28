package com.microsoft.serverless;

import java.util.ArrayList;
import java.util.List;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import com.microsoft.azure.serverless.functions.HttpResponseMessage;
import com.microsoft.azure.serverless.functions.OutputParameter;
import com.microsoft.azure.serverless.functions.annotation.Bind;

public class Miscellany {
    public static String echo(String body, @Bind("message") OutputParameter<String> message) {
        message.setValue("Hello, " + (body != null && !body.isEmpty() ? body : "[Unnamed]") + "!");
        return message.getValue();
    }

    public static String echo(@Bind("name") String param, String body, @Bind("message") OutputParameter<String> message) {
        List<String> targets = new ArrayList<>();
        if (param != null && !param.isEmpty()) { targets.add(param); }
        if (body != null && !body.isEmpty()) { targets.add(body); }
        return echo(String.join(" and ", targets), message);
    }

    public static HttpResponseMessage upload(byte[] data) {
        return new HttpResponseMessage(202, data.length + " bytes");
    }

    public static void heartbeat(ExecutionContext context) {
        context.getLogger().info("Java Timer Trigger Function Executed.");
    }

    public static void handleMessage(ExecutionContext context) {
        context.getLogger().info("Java Queue Trigger Function Executed.");
    }
}
