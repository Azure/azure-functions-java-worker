package com.microsoft.azure.functions.worker;

public class Util {
    public static boolean isTrue(String value) {
        if(value != null && (value.toLowerCase().equals("true") || value.toLowerCase().equals("1"))) {
            return true;
        }
        return false;
    }
}
