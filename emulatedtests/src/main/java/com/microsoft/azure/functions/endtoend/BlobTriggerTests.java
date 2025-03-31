package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;

import com.microsoft.azure.functions.*;

/**
 * Azure Functions with Azure Storage Blob.
 */
public class BlobTriggerTests {
    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("BlobTriggerToBlobTest")
    @StorageAccount("AzureWebJobsStorage")
     public void BlobTriggerToBlobTest(
        @BlobTrigger(name = "triggerBlob", path = "test-triggerinput-java-new/{name}", dataType = "binary") byte[] triggerBlob,
        @BindingName("name") String fileName,
        @BlobInput(name = "inputBlob", path = "test-input-java-new/{name}", dataType = "binary") byte[] inputBlob,
        @BlobOutput(name = "outputBlob", path = "test-output-java-new/{name}", dataType = "binary") OutputBinding<byte[]> outputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function BlobTriggerToBlobTest processed a blob.\n Name: " + fileName + "\n Size: " + triggerBlob.length + " Bytes");
        outputBlob.setValue(inputBlob);
    }   
    
    /*
     * Verified via Unit tests. Added test here for sample code
     */
    @FunctionName("BlobTriggerPOJOTest")
    @StorageAccount("AzureWebJobsStorage")
     public void BlobTriggerPOJOTest(
        @BlobTrigger(name = "triggerBlob", path = "test-triggerinputpojo-java/{name}") TestBlobData triggerBlobText,
        @BindingName("name") String fileName,        
        @BlobOutput(name = "outputBlob", path = "test-outputpojo-java/{name}") OutputBinding<TestBlobData> outputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function BlobTriggerPOJOTest processed a blob.\n Name: " + fileName + "\n Content: " + triggerBlobText.blobText);
        outputBlob.setValue(triggerBlobText);
    }
    
    /*
     * Verified via Unit tests. Added test here for sample code
     */
    @FunctionName("BlobTriggerStringTest")
    @StorageAccount("AzureWebJobsStorage")
     public void BlobTriggerStringTest(
        @BlobTrigger(name = "triggerBlob", path = "test-triggerinputstring-java/{name}") String triggerBlobText,
        @BindingName("name") String fileName,        
        @BlobOutput(name = "outputBlob", path = "test-outputstring-java/{name}") OutputBinding<String> outputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function BlobTriggerStringTest processed a blob.\n Name: " + fileName + "\n Content: " + triggerBlobText);
        outputBlob.setValue(triggerBlobText);
    }

    public static class TestBlobData {
      public String blobText;
  }
}
