package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Level;

import com.microsoft.azure.functions.worker.WorkerLogManager;
import com.microsoft.azure.functions.worker.exception.UserFunctionException;
import org.apache.commons.lang3.exception.*;

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

        try {
            return this.m.invoke(instance, this.args);
        } catch (InvocationTargetException ex) {
            WorkerLogManager.getSystemLogger().severe(String.format("exception happens in customer function: %s : %s", this.m.getName(), ex.getMessage()));
            return ExceptionUtils.rethrow(new UserFunctionException(this.m.getName(), ex.getCause()));
        } catch (Exception ex) {
            return ExceptionUtils.rethrow(ex);
        }
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
