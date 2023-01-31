package com.functions;

import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;

/**
 * Azure Functions with Azure Storage Blob.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-blob-trigger?tabs=java
 */
public class BlobTriggerFunction {
    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     * The location of the blob is provided in the path parameter. Example - test-triggerinput-java/{name} below
     */
    @FunctionName("BlobTrigger")
    @StorageAccount("AzureWebJobsStorage")
    public void BlobTriggerToBlobTest(
        @BlobTrigger(name = "triggerBlob", path = "test-triggerinput-java/{name}", dataType = "binary") byte[] triggerBlob,
        @BindingName("name") String fileName,
        @BlobInput(name = "inputBlob", path = "test-input-java/{name}", dataType = "binary") byte[] inputBlob,
        @BlobOutput(name = "outputBlob", path = "test-output-java/{name}", dataType = "binary") OutputBinding<byte[]> outputBlob,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Blob trigger function BlobTriggerToBlobTest processed a blob.\n Name: " + fileName + "\n Size: " + triggerBlob.length + " Bytes");
        outputBlob.setValue(inputBlob);
    }
}
