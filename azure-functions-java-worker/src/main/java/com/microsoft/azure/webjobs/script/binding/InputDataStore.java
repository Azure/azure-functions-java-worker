package com.microsoft.azure.webjobs.script.binding;

import java.util.*;

import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.rpc.messages.*;

/**
 * A warehouse storing all the input binding data from the user. You can use this class to query a single specific binding and only a single binding
 * by name, by type assignment or by type conversion.
 */
public final class InputDataStore {
    public InputDataStore(Iterable<ParameterBinding> sources) {
        for (ParameterBinding source : sources) {
            this.addSource(RpcInputData.parse(source));
        }
    }
    public <T> void addSource(T value) { this.addSource(new InputData<>(new BindingData.Value<>(value))); }
    private void addSource(InputData data) { this.inputs.add(data); }

    public Optional<BindingData.Value<?>> tryAssignAs(Class<?> target) {
        try { return Utility.single(this.inputs, in -> in.assignTo(target)); }
        catch (Exception ex) { return Optional.empty(); }
    }

    public Optional<BindingData.Value<?>> tryConvertTo(Class<?> target) {
        try { return Utility.single(this.inputs, in -> in.convertTo(target)); }
        catch (Exception ex) { return Optional.empty(); }
    }

    private List<InputData<?>> inputs = new ArrayList<>();
}
