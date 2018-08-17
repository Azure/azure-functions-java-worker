package com.microsoft.azure.functions.tests.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

/**
 * Azure Functions with Azure Storage Blob.
 */
public class BlobTriggerTests {
    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("BlobTrigger")
    @StorageAccount("AzureWebJobsStorage")
     public void blobTrigger(
        @BlobTrigger(name = "content", path = "myblob/{fileName}", dataType = "binary") byte[] content,
        @BindingName("fileName") String fileName,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function processed a blob.\n Name: " + fileName + "\n Size: " + content.length + " Bytes");
    }
    
    /**
     * This function will be invoked when a message add to queue. And the message is the file name to make a copy. Make sure the file exist or you will get an error
     */

    @FunctionName("BlobHandler")
    @StorageAccount("AzureWebJobsStorage")
    public void blobHandler(
        @QueueTrigger(name = "myBlobQueue", queueName = "myblobqueue", connection = "AzureWebJobsStorage") String myBlobQueue,
        @BlobInput(name = "myInputBlob", path = "myblob/{queueTrigger}", dataType = "binary") byte[] myInputBlob,
        @BlobOutput(name = "myOutputBlob", path = "myblob/{queueTrigger}-Copy", dataType = "binary") OutputBinding<byte[]> myOutputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Azure blob function is making a copy of " + myBlobQueue);
        myOutputBlob.setValue(myInputBlob);
    }
}
