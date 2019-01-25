package com.microsoft.azure.functions.worker.broker;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;

public class CoreTypeResolver {
  private static boolean isOutputParameter(Type target) {
    if (target instanceof ParameterizedType) {
      target = ((ParameterizedType) target).getRawType();
    }
    return (target instanceof Class<?>)
        && (OutputBinding.class.isAssignableFrom((Class<?>) target));
  }

  public static Type getParameterizedActualTypeArgumentsType(Type paramType) {
    if (paramType instanceof ParameterizedType) {
      ParameterizedType generics = (ParameterizedType) paramType;
      Type[] arguments = generics.getActualTypeArguments();
      if (arguments.length > 0) {
        return arguments[0];
      }
    }
    return Object.class;
  }

  public static boolean isValidOutputType(Type target) {
    if (!isOutputParameter(target)) {
      return false;
    }
    target = getParameterizedActualTypeArgumentsType(target);
    return !isOutputParameter(target);
  }

  public static boolean isHttpResponse(Type target) {
    return HttpResponseMessage.class.isAssignableFrom(getRuntimeClass(target));
  }

  public static Class<?> getRuntimeClass(Type type) {
    if (type instanceof ParameterizedType) {
      type = ((ParameterizedType) type).getRawType();
    }
    return (Class<?>) type;
  }

  public static String getAnnotationName(Parameter parameter) {
    Annotation[] annotations = parameter.getAnnotations();
    for (Annotation annotation : annotations) {
      CustomBinding customBindingAnnotation = annotation.annotationType()
          .getAnnotation(CustomBinding.class);
      if (customBindingAnnotation != null) {
        return getBindingNameFromCustomBindingAnnotation(customBindingAnnotation);
      } else if (annotation.toString().contains("com.microsoft.azure.functions.annotation")) {
        return getBindingNameFromAnnotation(annotation);
      }
    }
    return null;
  }

  private static String getBindingNameFromAnnotation(Annotation annotation) {
    for (Method annotationMethod : annotation.getClass().getMethods()) {
      if (annotationMethod.getName().equals("name")) {
        try {
          return (String) annotationMethod.invoke(annotation);
        } catch (Exception ex) {
          // Ignore
          return null;
        }
      }
    }
    return null;
  }

  private static String getBindingNameFromCustomBindingAnnotation(
      CustomBinding customBindingAnnotation) {
    try {
      return customBindingAnnotation.name();
    } catch (Exception ex) {
      // Ignore
      return null;
    }
  }

  static String getBindingNameAnnotation(Parameter param) {
    BindingName paramAnnotation = param.getDeclaredAnnotation(BindingName.class);
    if (paramAnnotation != null) {
      return paramAnnotation.value();
    }
    return new String("");
  }
}
