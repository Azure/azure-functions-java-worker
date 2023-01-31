package com.functions;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.util.*;

/**
 * Azure Functions with Azure Storage table.
 * https://docs.microsoft.com/en-us/azure/azure-functions/functions-bindings-storage-table-output?tabs=java
 */
public class TableFunction {
    /**
     * This function will be invoked when a new queue message is received.
     */
    @FunctionName("TableInput")
    public void tableInputJava(
        @QueueTrigger(name = "message", queueName = "mytablequeue", connection = "AzureWebJobsStorage") String message,
        @TableInput(name = "personEntity", tableName = "Person", rowKey = "{queueTrigger}", partitionKey = "firstPartition", connection = "AzureWebJobsStorage") String personEntity,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java Queue trigger function processed a new message: " + message);
        context.getLogger().info("Java Table Input function processed a Person entity:" + personEntity);
    }

    /**
     * This function will be invoked when a new http request is received at the specified path.
     */
    @FunctionName("TableOutput")
    public void tableOutputJava(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @TableOutput(name = "myOutputTable", tableName = "Person", connection = "AzureWebJobsStorage") OutputBinding<Person> myOutputTable,
        final ExecutionContext context
    ) {
        String httpbody = request.getBody().orElse("default");
        myOutputTable.setValue(new Person(httpbody + "Partition", httpbody + "Row", httpbody + "Name"));
        context.getLogger().info("Java Table Output function write a new entity into table Person with name: " + httpbody + "Name");
    }

    public static class Person {
        public String PartitionKey;
        public String RowKey;
        public String Name;

        public Person(String p, String r, String n) {
            this.PartitionKey = p;
            this.RowKey = r;
            this.Name = n;
        }
    }
}

