package com.microsoft.azure.webjobs.script.it.functions;


public class HttpBinary {
    public static String primitiveByteArray(byte[] data) {

        return "Received " + data.length + " bytes byte[] data";
    }

    public static String classByteArray(Byte[] data) {

        return "Received " + data.length + " bytes Byte[] data";
    }
}
