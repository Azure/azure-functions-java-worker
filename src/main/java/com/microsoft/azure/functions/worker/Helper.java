package com.microsoft.azure.functions.worker;

public class Helper {

    public static boolean isLoadAppLibsFirst() {
        String javaReverseLibLoading = System.getenv(Constants.FUNCTIONS_WORKER_JAVA_LOAD_APP_LIBS);
        return Util.isTrue(javaReverseLibLoading);
    }
    public static boolean isCustomURLClassLoader() {
        String customURLClassLoader = System.getenv(Constants.FUNCTIONS_WORKER_JAVA_V3_SINGLE_CLASSLOADER);
        return Util.isTrue(customURLClassLoader);
    }
}
