package com.microsoft.azure.functions.worker.binding;

import com.microsoft.azure.functions.rpc.messages.*;

public final class BindingDefinition {
    public BindingDefinition(String name, BindingInfo info) {
        this.name = name;
        this.direction = info.getDirection();
    }

    String getName() { return this.name; }
    boolean isInput() { return this.direction == BindingInfo.Direction.in || this.direction == BindingInfo.Direction.inout; }
    boolean isOutput() { return this.direction == BindingInfo.Direction.out || this.direction == BindingInfo.Direction.inout; }

    private final String name;
    private final BindingInfo.Direction direction;
    
}
