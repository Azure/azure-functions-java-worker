package com.microsoft.azure.functions.worker;

public interface IApplication {
    boolean logToConsole();
    String getHost();
    int getPort();
    Integer getMaxMessageSize();
    default String getFunctionsUri() {
        return null;
    }
}
