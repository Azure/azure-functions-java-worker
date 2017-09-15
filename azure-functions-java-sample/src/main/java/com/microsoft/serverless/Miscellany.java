package com.microsoft.serverless;

import java.util.Date;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import com.microsoft.azure.serverless.functions.HttpRequestMessage;
import com.microsoft.azure.serverless.functions.HttpResponseMessage;
import com.microsoft.azure.serverless.functions.OutputBinding;
import com.microsoft.azure.serverless.functions.annotation.BindingName;

public class Miscellany {
    public static HttpResponseMessage echo(HttpRequestMessage request) {
        HttpResponseMessage response = request.createResponse();
        response.setStatus(202);
        response.setBody(echo(request.getBody().toString()));
        return response;
    }

    public static String echo(@BindingName("name") String content) {
        return "Hello " + content + "!";
    }

    public static String heartbeat(ExecutionContext context) {
        context.getLogger().info("Java Timer Trigger Function Executed.");
        return String.format("I'm still alive :) [generated at %1$tD %1$tT, by heartbeat]", new Date());
    }

    public static String upload(byte[] data) {
        return data.length + " bytes";
    }

    public static String listMessages(@BindingName("storage") String storage, @BindingName("result") OutputBinding<String[]> result) {
        result.setValue(storage.split("\n"));
        return String.format("[Last consumed at %1$tD %1$tT]", new Date());
    }

    public static String handleMessage(@BindingName("message") String message, @BindingName("storage") String storage) {
        message = String.format("%s [handled at %2$tD %2$tT]", message, new Date());
        return storage + "\n" + message;
    }
}
