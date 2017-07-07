package com.microsoft.serverless;

import com.microsoft.azure.serverless.functions.HttpRequestMessage;
import com.microsoft.azure.serverless.functions.HttpResponseMessage;

public class Miscellany {
    public static String echo(String input) {
        return "Hello, " + input + "!";
    }

    public static String echo(HttpRequestMessage request) {
        String source = request.getQueryParameters().getOrDefault("name", null);
        if (source == null || source.isEmpty()) {
            source = request.getBody();
        }
        return echo(source);
    }

    public static HttpResponseMessage upload(byte[] data) {
        return new HttpResponseMessage(202, data.length + " bytes");
    }
}
