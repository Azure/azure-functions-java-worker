package com.azfs;

import com.microsoft.azure.functions.ExecutionContext;

public class DefaultCommunicatorImpl implements Communicator{
    @Override
    public boolean sendMessage(ExecutionContext context) {
        context.getLogger().info("Message sent out from injected communicator :) ");
        //add your own logic...
        return true;
    }
}
