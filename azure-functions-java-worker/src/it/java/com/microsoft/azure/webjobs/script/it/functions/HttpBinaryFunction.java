package com.microsoft.azure.webjobs.script.it.functions;


import com.microsoft.azure.serverless.functions.OutputBinding;
import com.microsoft.azure.serverless.functions.annotation.BindingName;

public class HttpBinaryFunction {
    public static String primitiveByteArray(byte[] data) {
        return "Received " + data.length + " bytes byte[] data";
    }

    public static String classByteArray(Byte[] data) {
        return "Received " + data.length + " bytes Byte[] data";
    }

    public static String upload(byte[] data, @BindingName("output") OutputBinding<byte[]> output) {
        output.setValue(data);
        return data.length + " bytes";
    }

}
