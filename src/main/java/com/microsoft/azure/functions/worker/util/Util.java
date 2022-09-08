package com.microsoft.azure.functions.worker.util;

public class Util {
    public static boolean isTrue(String value) {
        if(value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1"))) {
            return true;
        }
        return false;
    }

    public static String getJavaVersion() {
        return String.join(" - ", System.getProperty("java.home"), System.getProperty("java.version"));
    }
}