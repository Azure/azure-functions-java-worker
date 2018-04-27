package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.BindingName;
import com.microsoft.azure.webjobs.script.it.functions.dto.Point;

import java.util.*;

public class HttpFunction {
    public static HttpResponseMessage echo(HttpRequestMessage request) {
        return request.createResponse(202, request.getBody().toString());
    }

    public static String echo(@BindingName("name") String content) {
        return "Hello " + content + "!";
    }

    public static String handleSameName(@BindingName("req") HttpRequestMessage<String> request) {
        return request.getQueryParameters().get("req");
    }

    public static HttpResponseMessage<String> handleString(HttpRequestMessage<String> request) {
        return request.createResponse(280, "HttpFunction string content \"" + request.getBody() + "\"!");
    }

    public static HttpResponseMessage<Integer> handleInt(HttpRequestMessage<Integer> request) {
        return request.createResponse(281, request.getBody() + 111);
    }

    public static HttpResponseMessage<Integer[]> handleIntArray(HttpRequestMessage<Integer[]> request) {
        Integer[] result = Arrays.stream(request.getBody()).map(i -> i + 222).toArray(Integer[]::new);
        return request.createResponse(282, result);
    }

    public static HttpResponseMessage<Point> handlePojo(HttpRequestMessage<Point> request) {
        request.getBody().setX(request.getBody().getX() + 333);
        request.getBody().setY(request.getBody().getY() + 333);
        return request.createResponse(283, request.getBody());
    }

    public static HttpResponseMessage<Point[]> handlePojoArray(HttpRequestMessage<Point[]> request) {
        Arrays.stream(request.getBody()).forEach(p -> {
            p.setX(p.getX() + 444);
            p.setY(p.getY() + 444);
        });
        return request.createResponse(284, request.getBody());
    }

    public static HttpResponseMessage handleLegacy(HttpRequestMessage request) {
        return request.createResponse(285, request.getBody());
    }

    public static HttpResponseMessage<String> handleHeaders(HttpRequestMessage<Optional<Object>> request, ExecutionContext context) {
        context.getLogger().info("Request content is " + request.getBody());
        HttpResponseMessage<String> response = request.createResponse(286, "Check header value");
        response.addHeader("test-header", "test response header value");
        return response;
    }
}
