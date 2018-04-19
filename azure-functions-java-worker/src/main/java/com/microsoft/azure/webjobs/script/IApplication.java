package com.microsoft.azure.webjobs.script;

public interface IApplication {
    boolean logToConsole();
    String getHost();
    int getPort();
}
