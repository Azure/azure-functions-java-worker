package com.microsoft.azure.webjobs.script.it.functions;

public class LoggingFunction {
    public static String userModeException() {
        return String.format("Should throw exception here: %d", "It is a String");
    }
}