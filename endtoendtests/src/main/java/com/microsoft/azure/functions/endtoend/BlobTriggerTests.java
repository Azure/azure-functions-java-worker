package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;

import java.util.List;
import java.util.Optional;

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
        @BlobTrigger(name = "triggerBlob", path = "test-triggerinput-java/{name}", dataType = "binary") byte[] triggerBlob,
        @BindingName("name") String fileName,
        @BlobInput(name = "inputBlob", path = "test-input-java/{name}", dataType = "binary") byte[] inputBlob,
        @BlobOutput(name = "outputBlob", path = "test-output-java/{name}", dataType = "binary") OutputBinding<byte[]> outputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function processed a blob.\n Name: " + fileName + "\n Size: " + triggerBlob.length + " Bytes");        
        outputBlob.setValue(inputBlob);
    }    
}
