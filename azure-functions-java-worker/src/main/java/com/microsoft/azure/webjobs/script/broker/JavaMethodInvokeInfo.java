package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.webjobs.script.binding.*;

interface InstanceSupplier {
    Object get() throws Exception;
}

/**
 * Used to run the actual method with specific arguments using reflection.
 * Thread-Safety: Single thread.
 */
class JavaMethodInvokeInfo {
    private JavaMethodInvokeInfo() {}

    OutputDataStore invoke(InstanceSupplier instanceSupplier) throws Exception {
        Object instance = Modifier.isStatic(this.m.getModifiers()) ? null : instanceSupplier.get();
        Object retValue = this.m.invoke(instance, this.args);
        if (this.hasReturn) {
            this.outputs.tryGenerateReturn(retValue);
        }
        return this.outputs;
    }

    private Method m;
    private Object[] args;
    private OutputDataStore outputs;
    private boolean hasReturn;

    static class Builder {
        Builder() {
            this.info = new JavaMethodInvokeInfo();
            this.arguments = new LinkedList<>();
        }

        JavaMethodInvokeInfo build() {
            assert this.info.m != null;
            this.info.args = this.arguments.toArray();
            this.info.hasReturn = !this.info.m.getReturnType().equals(Void.class);
            return this.info;
        }

        void setMethod(Method method) { this.info.m = method; }
        void appendArgument(Object argument) { this.arguments.add(argument); }
        void setOutputs(OutputDataStore outputs) { this.info.outputs = outputs; }

        private JavaMethodInvokeInfo info;
        private List<Object> arguments;
    }
}
