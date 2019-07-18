package com.microsoft.azure.functions.worker.binding.tests;

import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl;

import java.lang.reflect.Type;
import java.util.List;

public class Utility {
    public static Type getActualType(Class clazz) {
        return ParameterizedTypeImpl.make(List.class, new Type[]{clazz}, null);
    }
}
