package com.microsoft.azure.functions.endtoend.springcloud;

import com.microsoft.azure.functions.ExecutionContext;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.function.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.function.Function;

@SpringBootApplication
public class Config {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Config.class, args);
    }

    @Bean
    public Function<String, String> echo() {
        return payload -> payload;
    }

    @Bean
    public Function<Message<String>, String> uppercase() {
        return message -> {
            String value = message.getPayload();
            ExecutionContext context = (ExecutionContext) message.getHeaders().get("executionContext");
            if(context != null)
                context.getLogger().info(new StringBuilder().append("Function: ")
                        .append(context.getFunctionName()).append(" is uppercasing ").append(value.toString()).toString());
            return value.toUpperCase();
        };
    }
}

