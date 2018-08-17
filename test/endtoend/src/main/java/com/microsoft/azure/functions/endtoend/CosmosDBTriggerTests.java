package com.microsoft.azure.functions.tests.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

/**
 * Azure Functions with Azure Cosmos DB.
 */
public class CosmosDBTriggerTests {
     /**
     * This function will be invoked when there are inserts or updates in the specified database and collection.
     */
    @FunctionName("CosmosDBTrigger")
    public void cosmosdbTrigger(
        @CosmosDBTrigger(
            name = "item",
            databaseName = "db",
            collectionName = "col",
            leaseCollectionName="leasecol",
            connectionStringSetting = "AzureCosmosDB",
            createLeaseCollectionIfNotExists = true
        ) Object item,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Cosmos DB trigger function executed. Get a new document: " + item.toString());
    }

    /**
    * This function will be invoked when a message add to the queue. The message contents are provided as the input to this function.
    */
    @FunctionName("CosmosDBInput")
    public void cosmosDBInput(
        @QueueTrigger(name = "message", queueName = "mydbqueue", connection = "AzureWebJobsStorage") String message,
        @CosmosDBInput(
            name = "item",
            databaseName = "db",
            collectionName = "col",
            connectionStringSetting = "AzureCosmosDB",
            id = "{queueTrigger}"
        ) Object item,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue Trigger post an id:" + message);
        context.getLogger().info("Java Cosmos DB Input function processed a document: " + item.toString());
    }

    /**
    * This function will be invoked when a post request with file to http://localhost:7071/api/CosmosDBOutput. A new document will add to the collection.
    */
    @FunctionName("CosmosDBOutput")
    public void cosmosDBOutOutput(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @CosmosDBOutput(
            name = "item",
            databaseName = "db",
            collectionName = "col",
            connectionStringSetting = "AzureCosmosDB"            
        ) OutputBinding<Document> item,
        final ExecutionContext context
    ) {
        String description = request.getBody().orElse("default message");
        item.setValue(new Document(UUID.randomUUID().toString(), description)); 
        context.getLogger().info("Java Cosmos DB Output function processed a document with description: " + description);
    }

    public static class Document {
        public String ID;
        public String Description;        

        public Document(String id, String description) {
            this.ID = id;
            this.Description = description;
        }
    }
}
