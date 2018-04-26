package com.microsoft.azure.serverless.functions.annotation;

import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.*;
import org.reflections.Reflections;

import static junit.framework.TestCase.*;

/**
 * Unit tests that enforce annotation contracts and conventions for Functions
 */
public class BindingTest {
    private static Set<Class<?>> annotations;
    private static String[] bindingAnnotationSuffix = new String[] { "Input", "Output", "Trigger" };

    @Test
    public void every_binding_annotation_should_have_name_method() {
        final String methodName = "name";

        for (Class<?> annotation : annotations) {
            Optional<Method> method = findMethod(annotation, methodName);
            assertTrue(method.isPresent());
        }
    }

    @Test
    public void every_binding_annotation_should_have_dataType_method() {
        final String methodName = "dataType";

        for (Class<?> annotation : annotations) {
            Optional<Method> method = findMethod(annotation, methodName);
            assertTrue(method.isPresent());
        }
    }

    /**
     * find all annotation bindings based on annotation suffix conventions defined in bindingAnnotationSuffix array
     */
    @Before
    public void findAllFunctionParameterBindingsInCore() {
        annotations = new Reflections(BindingTest.class.getPackage().getName())
                .getTypesAnnotatedWith(Target.class)
                .stream()
                .filter(t -> t.getSimpleName().endsWith(bindingAnnotationSuffix[0]) ||
                             t.getSimpleName().endsWith(bindingAnnotationSuffix[1]) ||
                             t.getSimpleName().endsWith(bindingAnnotationSuffix[2])).collect(Collectors.toSet());
    }

    private Optional<Method> findMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).findAny();
    }
}
