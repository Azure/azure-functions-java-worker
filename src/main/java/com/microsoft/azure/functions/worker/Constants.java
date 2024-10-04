package com.microsoft.azure.functions.worker;

/**
 * The Constants file for Java language worker
 */
public final class Constants {
    private Constants(){}

    public final static String FUNCTIONS_URI_OPTION = "functions-uri";
    public final static String FUNCTIONS_WORKER_ID_OPTION = "functions-worker-id";
    public final static String FUNCTIONS_REQUEST_ID_OPTION = "functions-request-id";
    public final static String FUNCTIONS_GRPC_MAX_MESSAGE_LENGTH_OPTION = "functions-grpc-max-message-length";
    public final static String FUNCTIONS_CONSOLE_LOG_OPTION = "functions-console-log";
    public final static String TRIGGER_METADATA_DOLLAR_REQUEST_KEY = "$request";
    public final static String JAVA_LIBRARY_DIRECTORY = "/annotationLib";
    public final static String JAVA_LIBRARY_ARTIFACT_ID = "azure-functions-java-library";
    public final static String HAS_IMPLICIT_OUTPUT_QUALIFIED_NAME = "com.microsoft.azure.functions.annotation.HasImplicitOutput";
    public final static String NULLABLE_VALUES_ENABLED_APP_SETTING = "FUNCTIONS_WORKER_NULLABLE_VALUES_ENABLED";
}
