package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import com.microsoft.azure.serverless.functions.OutputParameter;
import com.microsoft.azure.webjobs.script.*;
import com.microsoft.azure.webjobs.script.binding.*;

public class JavaMethodInvokeInfo {
    private JavaMethodInvokeInfo() {
    }

    public void invoke(InstanceSupplier<Object> instanceSupplier) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        Object instance = Modifier.isStatic(this.m.getModifiers()) ? null : instanceSupplier.get();
        Object retValue = this.m.invoke(instance, this.args);
        if (this.ret != null) {
            this.ret.setValue(retValue);
        }
    }

    private Method m;
    private OutputParameter<Object> ret;
    private Object[] args;

    public interface InstanceSupplier<T> {
        T get() throws IllegalAccessException, InstantiationException;
    }

    public static class Builder {
        public Builder() {
            this.info = new JavaMethodInvokeInfo();
            this.arguments = new LinkedList<>();
        }

        public JavaMethodInvokeInfo build() {
            assert this.info.m != null;
            this.info.args = this.arguments.toArray();
            return this.info;
        }

        public Builder setMethod(Method method) { this.info.m = method; return this; }
        public Builder appendArgument(Object argument) { this.arguments.add(argument); return this; }

        private JavaMethodInvokeInfo info;
        private List<Object> arguments;
    }
}
