package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class MethodBindInfo {

    private final Method entry;
    private final ParamBindInfo[] params;
    private final boolean isImplicitOutput;
    MethodBindInfo(Method m) {
        this.entry = m;
        this.params = Arrays.stream(this.entry.getParameters()).map(ParamBindInfo::new).toArray(ParamBindInfo[]::new);
        this.isImplicitOutput = checkImplicitOutput(params);
    }

    private static boolean checkImplicitOutput(ParamBindInfo[] params){
        for (ParamBindInfo paramBindInfo : params){
            if (paramBindInfo.isImplicitOutput()) return true;
        }
        return false;
    }

    public Method getEntry() {
        return entry;
    }

    public ParamBindInfo[] getParams() {
        return params;
    }

    public boolean isImplicitOutput() {
        return isImplicitOutput;
    }
}


