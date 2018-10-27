package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with Azure Storage Queue.
 */
public class QueueTriggerTests {
    /**
     * This function will be invoked when a http request is received. The message contents are provided as output to this function.
     */
    @FunctionName("QueueTriggerAndOutput")
    public void queuetriggerandoutput(
        @QueueTrigger(name = "message", queueName = "test-input-java", connection = "AzureWebJobsStorage") String message,
        @QueueOutput(name = "output", queueName = "test-output-java", connection = "AzureWebJobsStorage") OutputBinding<String> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: " + message);
        output.setValue(message);
    }

    @FunctionName("QueueTriggerAndOutputPOJO")
    public void queuetriggerandoutputPOJO(
        @QueueTrigger(name = "message", queueName = "test-input-java-pojo", connection = "AzureWebJobsStorage") TestData message,
        @QueueOutput(name = "output", queueName = "test-output-java-pojo", connection = "AzureWebJobsStorage") OutputBinding<TestData> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger POJO function processed a message: " + message.id);
        output.setValue(message);
    }

    @FunctionName("QueueTriggerMetadata")
    public void QueueTriggerMetadata(
        @QueueTrigger(name = "message", queueName = "test-input-java-metadata", connection = "AzureWebJobsStorage") String message,@BindingName("Id") String metadataId,
        @QueueOutput(name = "output", queueName = "test-output-java-metadata", connection = "AzureWebJobsStorage") OutputBinding<TestData> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: " + message + " whith metadaId:" + metadataId );
        TestData testData = new TestData();
        testData.id = metadataId;
        output.setValue(testData);
    }

    public static class TestData {
        public String id;
    }
}


