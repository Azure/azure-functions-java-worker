package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.Method;
import java.util.Arrays;

public final class MethodBindInfo {

    private final Method entry;
    private final ParamBindInfo[] params;
    private final boolean hasImplicitOutput;
    MethodBindInfo(Method m) {
        this.entry = m;
        this.params = Arrays.stream(this.entry.getParameters()).map(ParamBindInfo::new).toArray(ParamBindInfo[]::new);
        this.hasImplicitOutput = checkHasImplicitOutput();
    }

    private boolean checkHasImplicitOutput(){
        for (ParamBindInfo paramBindInfo : this.params){
            if (paramBindInfo.isHasImplicitOutput()) return true;
        }
        return false;
    }

    public Method getEntry() {
        return entry;
    }

    public ParamBindInfo[] getParams() {
        return params;
    }

    public boolean isHasImplicitOutput() {
        return hasImplicitOutput;
    }
}


