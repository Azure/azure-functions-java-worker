package com.microsoft.azure.webjobs.script.broker;

import java.lang.reflect.*;

import com.microsoft.azure.serverless.functions.*;

public class CoreTypeResolver {
    private static boolean isOutputParameter(Type target) {
        if (target instanceof ParameterizedType) {
            target = ((ParameterizedType)target).getRawType();
        }
        return (target instanceof Class<?>) && (OutputParameter.class.isAssignableFrom((Class<?>)target));
    }

    private static Type getOutputParameterArgument(Type outputParamType) {
        if (outputParamType instanceof ParameterizedType) {
            ParameterizedType generics = (ParameterizedType)outputParamType;
            Type[] arguments = generics.getActualTypeArguments();
            if (arguments.length > 0) { return arguments[0]; }
        }
        return Object.class;
    }

    public static boolean isValidOutputType(Type target) {
        if (!isOutputParameter(target)) { return false; }
        target = getOutputParameterArgument(target);
        return !isOutputParameter(target);
    }

    public static boolean isHttpResponse(Type target) {
        return HttpResponseMessage.class.isAssignableFrom(getRuntimeClass(target));
    }

    public static Class<?> getRuntimeClass(Type type) {
        if (type instanceof ParameterizedType) {
            type = ((ParameterizedType)type).getRawType();
        }
        return (Class<?>)type;
    }
}
