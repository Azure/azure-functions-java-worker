package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.*;

public final class BindingDefinition {
    public BindingDefinition(String name, BindingInfo info) {
        this.name = name;
        this.direction = info.getDirection();
        this.type = info.getType();
    }

    String getName() { return this.name; }
    boolean isInput() { return this.direction == BindingInfo.Direction.in || this.direction == BindingInfo.Direction.inout; }
    boolean isOutput() { return this.direction == BindingInfo.Direction.out || this.direction == BindingInfo.Direction.inout; }
    public boolean isHttpTrigger() { return isInput() && "httpTrigger".equalsIgnoreCase(this.type); }
    public boolean isHttpOutput() { return isOutput() && "http".equalsIgnoreCase(this.type); }

    private final String name;
    private final BindingInfo.Direction direction;
    private final String type;
    
}
