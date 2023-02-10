package com.azfs;

import com.microsoft.azure.functions.ExecutionContext;

public interface Communicator {
    boolean sendMessage(ExecutionContext context);
}
