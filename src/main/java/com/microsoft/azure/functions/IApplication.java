package com.microsoft.azure.functions;

public interface IApplication {
    boolean logToConsole();
    String getHost();
    int getPort();
    Integer getMaxMessageSize();
}
