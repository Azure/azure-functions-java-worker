package com.microsoft.azure.functions.endtoend;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.OutputBinding;
import com.microsoft.azure.functions.annotation.*;

import java.io.ByteArrayOutputStream;

/**
 * Azure Functions with Azure Storage Blob.
 */
public class BlobTriggerSdkTypesTests {
    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("BlobTriggerUsingBlobClientToBlobTest")
    @StorageAccount("AzureWebJobsStorage")
    public void BlobTriggerToBlobTest_BlobClient(
            @BlobTrigger(name = "triggerBlob", path = "test-triggerinput-blobclient/{name}", dataType = "binary") BlobClient triggerBlobClient,
            @BindingName("name") String fileName,
            @BlobOutput(name = "outputBlob", path = "test-output-java-new/testfile.txt", dataType = "binary") OutputBinding<byte[]> outputBlob,
            final ExecutionContext context
    ) {
        context.getLogger().info("BlobTriggerUsingBlobClient triggered for blob: " + fileName);

        // Download the blob content
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        triggerBlobClient.downloadStream(outputStream);

        // Set the downloaded content as output
        outputBlob.setValue(outputStream.toByteArray());
        context.getLogger().info("Uploaded blob " + fileName + " to container test-output-java-new/testfile.txt");
    }

    /**
     * This function will be invoked when a new or updated blob is detected at the specified path. The blob contents are provided as input to this function.
     */
    @FunctionName("BlobTriggerUsingBlobContainerClientToBlobTest")
    @StorageAccount("AzureWebJobsStorage")
    public void BlobTriggerToBlobTest_BlobContainerClient(
            @BlobTrigger(name = "triggerBlob", path = "test-triggerinput-blobcontclient/{name}", dataType = "binary") BlobContainerClient triggerBlobContainerClient,
            @BindingName("name") String fileName,
            @BlobOutput(name = "outputBlob", path = "test-output-java-new/testfile.txt", dataType = "binary") OutputBinding<byte[]> outputBlob,
            final ExecutionContext context
    ) {
        context.getLogger().info("BlobTriggerUsingBlobContainerClient triggered for blob: " + fileName);

        // Download the blob content
        BlobClient triggerBlobClient = triggerBlobContainerClient.getBlobClient(fileName);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        triggerBlobClient.downloadStream(outputStream);

        // Set the downloaded content as output
        outputBlob.setValue(outputStream.toByteArray());
        context.getLogger().info("Uploaded blob " + fileName + " to container test-output-java-new/testfile.txt");
    }
}
