package com.microsoft.azure.functions.endtoendtests;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Azure Functions with Azure Cosmos DB.
 */
public class CosmosDBTriggerTests {

    /**
     * This function will be invoked when a message add to the queue. The message
     * contents are provided as the input to this function.
     */
    @FunctionName("CosmosDBInput")
    public void cosmosDBInput(
            @QueueTrigger(name = "message", queueName = "mydbqueue", connection = "AzureWebJobsStorage") String message,
            @CosmosDBInput(name = "item", databaseName = "%CosmosDBDatabaseName%", collectionName = "%CosmosDBCollectionName%", connectionStringSetting = "AzureWebJobsCosmosDBConnectionString", id = "{queueTrigger}") Object item,
            final ExecutionContext context) {
        context.getLogger().info("Java Queue Trigger post an id:" + message);
        context.getLogger().info("Java Cosmos DB Input function processed a document: " + item.toString());
    }

    /**
     * This function will be invoked when a post request with file to
     * http://localhost:7071/api/CosmosDBOutput. A new document will add to the
     * collection.
     */
    @FunctionName("cosmosTriggerAndOutput")
    public void cosmosTriggerAndOutput(
            @CosmosDBTrigger(name = "itemIn", databaseName = "%CosmosDBDatabaseName%", collectionName = "ItemCollectionIn", leaseCollectionName = "leases", connectionStringSetting = "AzureWebJobsCosmosDBConnectionString", createLeaseCollectionIfNotExists = true) Object inputItem,
            @CosmosDBOutput(name = "itemOut", databaseName = "%CosmosDBDatabaseName%", collectionName = "ItemCollectionOut", connectionStringSetting = "AzureWebJobsCosmosDBConnectionString") OutputBinding<Document> outPutItem,
            final ExecutionContext context) {

        context.getLogger().info("Java Cosmos DB trigger function executed. Received document: " + inputItem);

        ArrayList inputItems = (ArrayList) inputItem;
        String objString = inputItems.get(0).toString();

        String[] arrOfStr = objString.split("=", 2);
        String[] arrOfStrWithId = arrOfStr[1].split(",", 2);

        ObjectMapper mapper = new ObjectMapper();
        String docId = arrOfStrWithId[0];
        context.getLogger().info("Writing to CosmosDB output binding Document id: " + docId);
        outPutItem.setValue(new Document(docId, "testdescription"));
    }

    public static class Document {
        public String id;
        public String Description;

        public Document(String id, String description) {
            this.id = id;
            this.Description = description;
        }
    }
}
