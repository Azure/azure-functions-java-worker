package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.util.*;

interface InstanceSupplier {
    Object get() throws Exception;
}

/**
 * Used to run the actual method with specific arguments using reflection.
 * Thread-Safety: Single thread.
 */
class JavaMethodInvokeInfo {
    private JavaMethodInvokeInfo() {}

    Object invoke(InstanceSupplier instanceSupplier) throws Exception {
        Object instance = Modifier.isStatic(this.m.getModifiers()) ? null : instanceSupplier.get();
        return this.m.invoke(instance, this.args);
    }

    private Method m;
    private Object[] args;

    static class Builder {
        Builder() {
            this.info = new JavaMethodInvokeInfo();
            this.arguments = new LinkedList<>();
        }

        JavaMethodInvokeInfo build() {
            assert this.info.m != null;
            this.info.args = this.arguments.toArray();
            return this.info;
        }

        void setMethod(Method method) { this.info.m = method; }
        void appendArgument(Object argument) { this.arguments.add(argument); }

        private JavaMethodInvokeInfo info;
        private List<Object> arguments;
    }
}
