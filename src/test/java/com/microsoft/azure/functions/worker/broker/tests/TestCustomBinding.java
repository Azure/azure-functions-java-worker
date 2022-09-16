package com.microsoft.azure.functions.worker.broker.tests;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.ElementType;

import com.microsoft.azure.functions.annotation.CustomBinding;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@CustomBinding(direction = "in", name = "", type = "customBinding")
public @interface TestCustomBinding {
   String index();
   String path();
   String name();
}
