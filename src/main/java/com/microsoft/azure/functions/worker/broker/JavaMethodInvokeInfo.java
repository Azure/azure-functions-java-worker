package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.*;
import java.util.*;

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
            if (ex.getCause() == null) {
                return ExceptionUtils.rethrow(ex);
            }
            Throwable userModeEx = ex.getCause();
            StackTraceElement[] parentStackTraces = ex.getStackTrace();
            StackTraceElement[] userStackTraces = userModeEx.getStackTrace();
            int lastUserModeStackFrame = userStackTraces.length - 1;
            if (lastUserModeStackFrame > parentStackTraces.length - 1) {
                for (int parentFrame = parentStackTraces.length - 1; parentFrame >= 0; parentFrame--, lastUserModeStackFrame--) {
                    if (!parentStackTraces[parentFrame].equals(userStackTraces[lastUserModeStackFrame])) {
                        break;
                    }
                }
            }
            userModeEx.setStackTrace(Arrays.copyOf(userStackTraces, lastUserModeStackFrame + 1));
            return ExceptionUtils.rethrow(userModeEx);
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
