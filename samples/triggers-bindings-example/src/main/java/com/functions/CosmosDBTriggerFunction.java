package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Azure Functions with Azure Cosmos DB.
 */
public class CosmosDBTriggerFunction {

    /**
     * This function will be invoked when a message is posted to
     * /api/CosmosDBInputId?docId={docId} contents are provided as the input to this
     * function.
     */
    @FunctionName("CosmosDBInputId")
    public HttpResponseMessage CosmosDBInputId(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                               @CosmosDBInput(name = "item", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionIn", connection = "AzureWebJobsCosmosDBConnectionString", id = "{docId}") String item,
                                               final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a request.");

        if (item != null) {
            return request.createResponseBuilder(HttpStatus.OK).body("Received Document" + item).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected item in ItemsCollectionIn").build();
        }
    }

    @FunctionName("CosmosDBInputIdPOJO")
    public HttpResponseMessage CosmosDBInputIdPOJO(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                                   @CosmosDBInput(name = "item", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionIn", connection = "AzureWebJobsCosmosDBConnectionString", id = "{docId}") Document item,
                                                   final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a request.");

        if (item != null) {
            return request.createResponseBuilder(HttpStatus.OK).body("Received Document with Id " + item.id).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected item in ItemsCollectionIn").build();
        }
    }


    /**
     * This function will be invoked when a message is posted to
     * /api/CosmosDBInputQuery?name=joe Receives input with list of items matching
     * the sqlQuery
     */
    @FunctionName("CosmosDBInputQueryPOJOArray")
    public HttpResponseMessage CosmosDBInputQueryPOJOArray(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                                           @CosmosDBInput(name = "items", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionIn", connection = "AzureWebJobsCosmosDBConnectionString", sqlQuery = "SELECT f.id, f.name FROM f WHERE f.name = {name}") Document[] items,
                                                           @CosmosDBOutput(name = "itemsOut", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionOut", connection = "AzureWebJobsCosmosDBConnectionString") OutputBinding<Document[]> itemsOut,
                                                           final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        if (items.length >= 2) {
            itemsOut.setValue(items);
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + items[0].name).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected items in CosmosDB input list").build();
        }
    }

    @FunctionName("CosmosDBInputQueryPOJOList")
    public HttpResponseMessage CosmosDBInputQueryPOJOList(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                                          @CosmosDBInput(name = "item", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionIn", connection = "AzureWebJobsCosmosDBConnectionString", sqlQuery = "SELECT f.id, f.name FROM f WHERE f.name = {name}") List<Document> items,
                                                          @CosmosDBOutput(name = "itemsOut", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionOut", connection = "AzureWebJobsCosmosDBConnectionString") OutputBinding<List<Document>> itemsOut,
                                                          final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        if (items.size() >= 2) {
            itemsOut.setValue(items);
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + items.get(0).name).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected items in CosmosDB input list").build();
        }
    }

    @FunctionName("CosmosDBInputQuery")
    public HttpResponseMessage CosmosDBInputQuery(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
        HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
                                                  @CosmosDBInput(name = "item", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemsCollectionIn", connection = "AzureWebJobsCosmosDBConnectionString", sqlQuery = "SELECT f.id, f.name FROM f WHERE f.name = {name}") List<String> items,
                                                  final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (items.size() >= 2) {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Did not find expected items in CosmosDB input list").build();
        }
    }

    /**
     * This function will be invoked when a post request with file to
     * http://localhost:7071/api/CosmosDBOutput. A new document will add to the
     * container.
     */
    @FunctionName("CosmosTriggerAndOutput")
    public void CosmosTriggerAndOutput(
        @CosmosDBTrigger(name = "itemIn", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemCollectionIn", leaseContainerName = "leases", connection = "AzureWebJobsCosmosDBConnectionString", createLeaseContainerIfNotExists = true) Object inputItem,
        @CosmosDBOutput(name = "itemOut", databaseName = "%CosmosDBDatabaseName%", containerName = "ItemCollectionOut", connection = "AzureWebJobsCosmosDBConnectionString") OutputBinding<Document> outPutItem,
        final ExecutionContext context) {

        context.getLogger().info("Java Cosmos DB trigger function executed. Received document: " + inputItem);

        ArrayList inputItems = (ArrayList) inputItem;
        String objString = inputItems.get(0).toString();
        String[] arrOfStr = objString.split("=", 2);
        String[] arrOfStrWithId = arrOfStr[1].split(",", 2);
        String docId = arrOfStrWithId[0];

        context.getLogger().info("Writing to CosmosDB output binding Document id: " + docId);
        Document testDoc = new Document();
        testDoc.id = docId;
        testDoc.Description = "testdescription";
        outPutItem.setValue(testDoc);
    }

    public class Document {
        public String id;
        public String name;
        public String Description;
    }
}
