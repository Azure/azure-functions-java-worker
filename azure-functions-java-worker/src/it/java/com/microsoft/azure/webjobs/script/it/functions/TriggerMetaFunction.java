package com.microsoft.azure.webjobs.script.it.functions;

import com.microsoft.azure.serverless.functions.ExecutionContext;
import com.microsoft.azure.serverless.functions.annotation.BindingName;

public class TriggerMetaFunction {
    public static String handleHttpRoute(@BindingName("category") String category, @BindingName("id") int id) {
        return "Here is the information for [Category: " + category + "] and [ID: " + id + "]";
    }

    public static void handleBlobMetadata(
        byte[] content, @BindingName("blobname") String filename, @BindingName("blobextension") String ext, ExecutionContext context
    ) {
        context.getLogger().info("Received " + content.length + " bytes for blob [Name: " + filename + "] [Extension: " + ext + "]");
    }
}
