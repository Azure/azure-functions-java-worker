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
		System.out.println("**********************************CoreTypeResolver.getAnnotationName");
		Annotation[] annotations = parameter.getAnnotations();
		String annotationName = null;
		System.out.println("Annotation count: " + annotations.length);
		for (Annotation annotation : annotations) {
			System.out.println("Annotation.toString(): "+ annotation.toString());
			if (annotation.toString().contains("com.microsoft.azure.functions.annotation")) {
				System.out.println("++++++++++++++++++step0:");
				annotationName = getBindingNameFromAnnotation(annotation);
			}
			if (annotationName == null) {
				CustomBinding customBindingAnnotation = annotation.annotationType().getAnnotation(CustomBinding.class);
				System.out.println("++++++++++++++++++step1:");
				if (customBindingAnnotation != null) {
					System.out.println("++++++++++++++++++step2:");
					annotationName = getBindingNameFromAnnotation(annotation);
					if (annotationName == null) {
						System.out.println("++++++++++++++++++step3:");
						annotationName = getBindingNameFromCustomBindingAnnotation(customBindingAnnotation);
					}
				}
			}
		}

		System.out.println("Parameter.name: " + parameter.getName() + " Parameter.type: " + parameter.getType().getTypeName() + " AnnotationName: " + annotationName);
		System.out.println("********************************end of CoreTypeResolver.getAnnotationName");
		return annotationName;
	}

	private static String getBindingNameFromAnnotation(Annotation annotation) {
		System.out.println("-------------- getBindingNameFromAnnotation in " + annotation.toString());
		System.out.println("-------------- annotation.getClass() :" + annotation.getClass().getTypeName());
		System.out.println("---------------annotation.getClass().getMethods().count: " + annotation.getClass().getMethods().length);

		if (annotation.toString().contains("HttpTrigger")) { // workaround
			return "req";
		}
		for (Method annotationMethod : annotation.getClass().getMethods()) {
			System.out.println("-------------------- annotationMethod.getName(): " + annotationMethod.getName());
			if (annotationMethod.getName().equals("name")) {
				try {
					System.out.println("-------Step0");
					return (String) annotationMethod.invoke(annotation);
				} catch (Exception ex) {
					// Ignore
					System.out.println("-------Step1");
					ex.printStackTrace(); // Added.
					return null;
				}
			}
		}
		System.out.println("-------Step2");
		return null;
	}

	private static String getBindingNameFromCustomBindingAnnotation(CustomBinding customBindingAnnotation) {
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
