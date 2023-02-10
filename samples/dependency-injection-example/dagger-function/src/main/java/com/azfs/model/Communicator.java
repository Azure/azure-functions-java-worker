package com.azfs.model;

import com.microsoft.azure.functions.ExecutionContext;

public class Communicator {
    private final String id;

    public Communicator(String id) {
        this.id = id;
    }

    public void communicate(ExecutionContext context){
        context.getLogger().info("Message sent out from injected communicator :) ");
        //add your own logics ...
    }
}
