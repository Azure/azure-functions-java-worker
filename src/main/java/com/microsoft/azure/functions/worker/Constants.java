package com.microsoft.azure.functions.worker;

/**
 * The Constants file for Java language worker
 */
public final class Constants {
    private Constants(){}
    public final static String TRIGGER_METADATA_DOLLAR_REQUEST_KEY = "$request";
    public final static String FUNCTIONS_WORKER_DIRECTORY = "FUNCTIONS_WORKER_DIRECTORY";
    public final static String FUNCTIONS_WORKER_JAVA_LOAD_APP_LIBS = "FUNCTIONS_WORKER_JAVA_LOAD_APP_LIBS";
    public final static String FUNCTIONS_WORKER_JAVA_V3_SINGLE_CLASSLOADER = "FUNCTIONS_WORKER_JAVA_V3_SINGLE_CLASSLOADER";
    public final static String JAVA_LIBRARY_DIRECTORY = "/annotationLib";
    public final static String JAVA_LIBRARY_ARTIFACT_ID = "azure-functions-java-library";
}
