package com.microsoft.azure.webjobs.script.it.functions;

import java.util.Optional;

public class OptionalFunction {
    public static String testBodyOptional(java.util.Optional<String> httpBody) {
        return httpBody
            .map(content -> "Nice! The optional content is \"" + content + "\"")
            .orElse("There is no optional content");
    }

    public static String testBodyNull(String httpBody) {
        if (httpBody != null) {
            return "Nice! The http body string is \"" + httpBody + "\"";
        }
        return "HttpFunction body string is null";
    }
}
