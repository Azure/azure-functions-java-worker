package com.microsoft.azure.functions.endtoend.springcloud;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;

import java.util.Optional;

import org.springframework.cloud.function.adapter.azure.FunctionInvoker;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;


public class SpringCloudHandler extends FunctionInvoker<Message<String>, String> {

    @FunctionName("echo")
    public String echo(@HttpTrigger(name = "req", methods = {HttpMethod.GET,
            HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                          ExecutionContext context) {
        context.getLogger().info("Java Spring Cloud echo function processed a request.");
        String query = request.getQueryParameters().get("name");
        query = query != null ? query : "echo";
        Message<String> message = MessageBuilder.withPayload(request.getBody().orElse(query)).copyHeaders(request.getHeaders()).build();
        return handleRequest(message, context);
    }

    @FunctionName("uppercase")
    public String uppercase(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            ExecutionContext context
    ) {
        context.getLogger().info("Java Spring Cloud uppercase function processed a request.");
        String query = request.getQueryParameters().get("name");
        query = query != null ? query : "uppercase";
        Message<String> message = MessageBuilder.withPayload(request.getBody().orElse(query))
                .copyHeaders(request.getHeaders()).build();
        return handleRequest(message, context);
    }

}
