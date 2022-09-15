package com.microsoft.azure.functions.worker.broker;

import java.lang.annotation.*;
import java.lang.reflect.*;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.worker.Constants;

public class CoreTypeResolver {
	private static boolean isOutputParameter(Type target) {
		if (target instanceof ParameterizedType) {
			target = ((ParameterizedType) target).getRawType();
		}
		return (target instanceof Class<?>) && (OutputBinding.class.isAssignableFrom((Class<?>) target));
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
		String annotationName = null;

		for (Annotation annotation : annotations) {
			if (annotation.toString().contains("com.microsoft.azure.functions.annotation")) {
				annotationName = getBindingNameFromAnnotation(annotation);
			}

			if (annotationName == null) {
				Annotation customBindingAnnotation = null;
				for (Annotation item : annotation.annotationType().getAnnotations()){
					if (item.annotationType().getName().equals("com.microsoft.azure.functions.annotation.CustomBinding")) {
						customBindingAnnotation = item;
						break;
					}
				}
				if (customBindingAnnotation != null) {
					annotationName = getBindingNameFromAnnotation(annotation);
					if (annotationName == null) {
						try {
							Method name = customBindingAnnotation.annotationType().getMethod("name");
							annotationName = getBindingNameFromCustomBindingAnnotation(customBindingAnnotation, name);
						} catch (NoSuchMethodException ex) {
							// Ignore
						}
					}
				}
			}
		}
		return annotationName;
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

	private static String getBindingNameFromCustomBindingAnnotation(Annotation customBindingAnnotation, Method name) {
		try {
			return (String) name.invoke(customBindingAnnotation);
		} catch (Exception ex) {
			// Ignore
			return null;
		}
	}

	static String getBindingNameAnnotation(Parameter param) {
		Annotation bindingNameAnnotation = null;
		for (Annotation item : param.getAnnotations()){
			if (item.annotationType().getName().equals("com.microsoft.azure.functions.annotation.BindingName")){
				bindingNameAnnotation = item;
				break;
			}
		}
		if (bindingNameAnnotation != null) {
			String returnValue = null;
			try {
				Method value = bindingNameAnnotation.annotationType().getMethod("value");
				returnValue = (String) value.invoke(bindingNameAnnotation);
			} catch (Exception ex) {
				// Ignore
			}
			return returnValue;
		}
		return new String("");
	}

	static boolean checkImplicitOutput(Parameter parameter) {
		Annotation[] annotations = parameter.getAnnotations();
		for (Annotation annotation : annotations) {
			for (Annotation item : annotation.annotationType().getAnnotations()) {
				if (item.annotationType().getName().equals(Constants.HAS_IMPLICIT_OUTPUT_QUALIFIED_NAME)) {
					if (hasImplicitOutput(item)) return true;
				}
			}
		}
		return false;
	}

	static boolean hasImplicitOutput(Annotation annotation){
		try {
			Method hasImplicitOutputMethod = annotation.annotationType().getMethod("value");
			boolean hasImplicitOutput = (boolean) hasImplicitOutputMethod.invoke(annotation);
			return hasImplicitOutput;
		} catch (Exception e) {
			//do nothing
		}
		return false;
	}
}
