package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.util.*;

import com.microsoft.azure.webjobs.script.binding.*;

interface InstanceSupplier {
    Object get() throws Exception;
}

class JavaMethodInvokeInfo {
    private JavaMethodInvokeInfo() {}

    void invoke(InstanceSupplier instanceSupplier, OutputDataStore outputs) throws Exception {
        Object instance = Modifier.isStatic(this.m.getModifiers()) ? null : instanceSupplier.get();
        Object retValue = this.m.invoke(instance, this.args);
        outputs.tryGenerateReturn(retValue);
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
