package com.microsoft.azure.functions.worker.broker;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class MethodBindInfo {

    private final Method method;
    private final List<ParamBindInfo> params;
    private final boolean hasImplicitOutput;
    MethodBindInfo(Method method) {
        this.method = method;
        this.params = Arrays.stream(method.getParameters()).map(ParamBindInfo::new).collect(Collectors.toList());
        this.hasImplicitOutput = checkImplicitOutput(params);
    }

    private static boolean checkImplicitOutput(List<ParamBindInfo> params){
        return params.stream().anyMatch(ParamBindInfo::isImplicitOutput);
    }

    public Method getMethod() {
        return method;
    }

    public List<ParamBindInfo> getParams() {
        return params;
    }

    public boolean hasImplicitOutput() {
        return hasImplicitOutput;
    }
}


