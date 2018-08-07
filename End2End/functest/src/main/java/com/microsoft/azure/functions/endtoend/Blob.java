package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.annotation.BindingName;
import com.microsoft.azure.functions.OutputBinding;

public class Blob {

    public static void blobTrigger(
        byte[] myBlob, 
        @BindingName("filename") String filename, 
        ExecutionContext context
    ) {
        context.getLogger().info("blob trigger function processed blob.\n Name: " + filename + "\n Blob size:" + myBlob.length + " Bytes");
    } 

    public static void blobHandler(
        @BindingName("myBlobQueue") String myBlobQueue,
        @BindingName("myInputBlob") String myInputBlob,
        @BindingName("myOutputBlob") OutputBinding<String> myOutputBlob,
        ExecutionContext context
        ) {    
        context.getLogger().info("Java Queue trigger function processed:" + myBlobQueue);
        myOutputBlob.setValue(myInputBlob);
    } 
}