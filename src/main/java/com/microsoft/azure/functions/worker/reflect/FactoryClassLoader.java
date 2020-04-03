package com.microsoft.azure.functions.worker.reflect;

import org.apache.commons.lang3.SystemUtils;

public class FactoryClassLoader {
    public ClassLoaderProvider createClassLoaderProvider(){
        if(SystemUtils.IS_JAVA_1_8) {
            return new DefaultClassLoaderProvider();
        } else {
            return new EnhancedClassLoaderProvider();
        }
    }
}
