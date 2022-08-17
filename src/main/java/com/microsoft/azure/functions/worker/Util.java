package com.microsoft.azure.functions.worker;

public class Util {
    public static boolean isTrue(String value) {
        if(value != null && (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("1"))) {
            return true;
        }
        return false;
    }

    public static String getJavaVersion() {
        return String.join(" - ", getJavaHome(), System.getProperty("java.version"));
    }

    public static String getJavaHome() {
        return System.getProperty("java.home");
    }
}