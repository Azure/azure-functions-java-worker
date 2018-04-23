package com.microsoft.azure.serverless.functions.annotation;

import java.lang.reflect.Method;
import java.util.*;

import org.junit.*;
import org.reflections.Reflections;

import static junit.framework.TestCase.*;

/**
 * Unit tests that enforce annotation contracts and conventions for Functions
 */
public class BindingTest {
    private static Set<Class<?>> annotations;


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

    @Test
    public void every_TRIGGER_binding_annotation_should_have_Trigger_suffix() {
        final String expectedSuffix = "Trigger";
        final BindingType expectedBindingType = BindingType.TRIGGER;

        annotations.stream().filter(t -> t.getAnnotation(Binding.class).value() == expectedBindingType)
                .forEach(annotation -> {
                    String name = annotation.getSimpleName();
                    String actualSuffix = name.substring(name.length() - expectedSuffix.length(), name.length());

                    assertEquals(expectedSuffix, actualSuffix);
                });
    }

    @Test
    public void every_OUTPUT_binding_annotation_should_have_Output_suffix() {
        final String expectedSuffix = "Output";
        final BindingType expectedBindingType = BindingType.OUTPUT;

        annotations.stream().filter(t -> t.getAnnotation(Binding.class).value() == expectedBindingType)
                .forEach(annotation -> {
                    String name = annotation.getSimpleName();
                    String actualSuffix = name.substring(name.length() - expectedSuffix.length(), name.length());

                    assertEquals(expectedSuffix, actualSuffix);
                });
    }

    @Test
    public void every_INPUT_binding_annotation_should_have_Input_suffix() {
        final String expectedSuffix = "Input";
        final BindingType expectedBindingType = BindingType.INPUT;

        annotations.stream().filter(t -> t.getAnnotation(Binding.class).value() == expectedBindingType)
            .forEach(annotation -> {
                String name = annotation.getSimpleName();
                String actualSuffix = name.substring(name.length() - expectedSuffix.length(), name.length());

                assertEquals(expectedSuffix, actualSuffix);
            });
    }

    @Before
    public void findAllFunctionParameterBindingsInCore() {
        annotations = new Reflections(BindingTest.class.getPackage().getName())
                .getTypesAnnotatedWith(Binding.class);
    }

    private Optional<Method> findMethod(Class<?> type, String name) {
        return Arrays.stream(type.getMethods()).filter(m -> m.getName().equals(name)).findAny();
    }
}
