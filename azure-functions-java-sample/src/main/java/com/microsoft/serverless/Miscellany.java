package com.microsoft.serverless;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import com.microsoft.azure.serverless.functions.HttpResponseMessage;
import com.microsoft.azure.serverless.functions.OutputParameter;
import com.microsoft.azure.serverless.functions.annotation.Bind;

public class Miscellany {
    public static String echo(String body, @Bind("message") OutputParameter<String> message) {
        String result = String.format("Hello, %s!", (body != null && !body.isEmpty() ? body : "[Unnamed]"));
        message.setValue(String.format("%s [generated at %2$tD %2$tT, by echo]", result, new Date()));
        return result;
    }

    public static String echo(@Bind("name") String param, String body, @Bind("message") OutputParameter<String> message) {
        List<String> targets = new ArrayList<>();
        if (param != null && !param.isEmpty()) { targets.add(param); }
        if (body != null && !body.isEmpty()) { targets.add(body); }
        return echo(String.join(" and ", targets), message);
    }

    public static String heartbeat(ExecutionContext context) {
        context.getLogger().info("Java Timer Trigger Function Executed.");
        return String.format("I'm still alive :) [generated at %1$tD %1$tT, by heartbeat]", new Date());
    }

    public static HttpResponseMessage upload(byte[] data) {
        return new HttpResponseMessage(202, data.length + " bytes");
    }

    public static String listMessages(@Bind("storage") String storage, @Bind("result") OutputParameter<String[]> result) {
        result.setValue(storage.split("\n"));
        return String.format("[Last consumed at %1$tD %1$tT]", new Date());
    }

    public static String handleMessage(@Bind("message") String message, @Bind("storage") String storage) {
        message = String.format("%s [handled at %2$tD %2$tT]", message, new Date());
        return storage + "\n" + message;
    }
}
