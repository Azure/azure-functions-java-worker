package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.*;

/**
 * Azure Functions with Azure Storage Queue.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-queue-trigger?tabs=java
 */
public class QueueTriggerFunction {
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

    @FunctionName("QueueOutputPOJOList")
    public HttpResponseMessage QueueOutputPOJOList(@HttpTrigger(name = "req", methods = {HttpMethod.GET,
        HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                                   @QueueOutput(name = "output", queueName = "test-output-java-pojo", connection = "AzureWebJobsStorage") OutputBinding<List<TestData>> itemsOut,
                                                   final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        String query = request.getQueryParameters().get("queueMessageId");
        String queueMessageId = request.getBody().orElse(query);
        itemsOut.setValue(new ArrayList<TestData>());
        if (queueMessageId != null) {
            TestData testData1 = new TestData();
            testData1.id = "msg1" + queueMessageId;
            TestData testData2 = new TestData();
            testData2.id = "msg2" + queueMessageId;

            itemsOut.getValue().add(testData1);
            itemsOut.getValue().add(testData2);

            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + queueMessageId).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected items in CosmosDB input list").build();
        }
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
        @QueueTrigger(name = "message", queueName = "test-input-java-metadata", connection = "AzureWebJobsStorage") String message, @BindingName("Id") String metadataId,
        @QueueOutput(name = "output", queueName = "test-output-java-metadata", connection = "AzureWebJobsStorage") OutputBinding<TestData> output,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a message: " + message + " whith metadaId:" + metadataId);
        TestData testData = new TestData();
        testData.id = metadataId;
        output.setValue(testData);
    }

    public static class TestData {
        public String id;
    }
}


