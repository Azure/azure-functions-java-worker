package com.microsoft.azure.webjobs.script.broker;

import java.io.*;
import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.*;
import org.apache.commons.lang3.*;

import com.microsoft.azure.serverless.functions.*;
import com.microsoft.azure.serverless.functions.annotation.*;

public class CoreTypeResolver {
    private static boolean isOutputParameter(Type target) {
        if (target instanceof ParameterizedType) {
            target = ((ParameterizedType)target).getRawType();
        }
        return (target instanceof Class<?>) && (OutputBinding.class.isAssignableFrom((Class<?>)target));
    }

    public static Type getOutputParameterArgument(Type outputParamType) {
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

    static String getBindingName(Parameter parameter) {
        for (Function<Parameter, Optional<String>> bindingNameSupplier : BINDING_NAME_SUPPLIERS) {
            Optional<String> bindingName = bindingNameSupplier.apply(parameter);
            if (bindingName.isPresent()) {
                return bindingName.get();
            }
        }
        return null;
    }

    private static final List<Function<Parameter, Optional<String>>> BINDING_NAME_SUPPLIERS;
    static {
        BINDING_NAME_SUPPLIERS = new ArrayList<>();
        BINDING_NAME_SUPPLIERS.add(p -> Optional.ofNullable(p.getAnnotation(BindingName.class)).map(BindingName::value));
        try {
            ClassPath coreClasses = ClassPath.from(ClassLoader.getSystemClassLoader());
            String annotationsPackage = ClassUtils.getPackageName(BindingName.class);
            for (ClassInfo annotationInfo : coreClasses.getTopLevelClasses(annotationsPackage)) {
                try {
                    String annotationName = annotationInfo.getSimpleName();
                    if (annotationName.endsWith("Input") || annotationName.endsWith("Output") || annotationName.endsWith("Trigger")) {
                        @SuppressWarnings("unchecked")
                        final Class<? extends Annotation> annotation = (Class<? extends Annotation>) annotationInfo.load();
                        final Method getNameMethod = annotation.getMethod("name");
                        BINDING_NAME_SUPPLIERS.add(p -> {
                            try {
                                return Optional.ofNullable((String) getNameMethod.invoke(p.getAnnotation(annotation)));
                            } catch (Exception ex) {
                                return Optional.empty();
                            }
                        });
                    }
                } catch (NoSuchMethodException ex) {
                }
            }
        } catch (IOException ex) {
        }
    }
}
