package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.google.gson.Gson;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.sql.annotation.CommandType;
import com.microsoft.azure.functions.sql.annotation.SQLInput;
import com.microsoft.azure.functions.sql.annotation.SQLOutput;
import com.microsoft.azure.functions.sql.annotation.SQLTrigger;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Azure Functions with Azure SQL DB.
 */
public class SqlTriggerTests {

    @FunctionName("GetProducts")
    public HttpResponseMessage GetProducts(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
            HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @SQLInput(name = "products", commandText = "SELECT * FROM Products",
            commandType = CommandType.Text, connectionStringSetting = "AzureWebJobsSqlConnectionString") Product[] products,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a request.");

        if (products != null) {
            return request.createResponseBuilder(HttpStatus.OK).body(products).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Did not find expected products in table Products").build();
        }
    }

    @FunctionName("GetProducts2")
    public HttpResponseMessage GetProducts2(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
            HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @SQLInput(name = "products", commandText = "SELECT * FROM Products2",
            commandType = CommandType.Text, connectionStringSetting = "AzureWebJobsSqlConnectionString") Product[] products,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a request.");

        if (products != null) {
            return request.createResponseBuilder(HttpStatus.OK).body(products).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Did not find expected products in table Products2").build();
        }
    }

    @FunctionName("AddProduct")
    public HttpResponseMessage AddProduct(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
            HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @SQLOutput(name = "product", commandText = "Products", connectionStringSetting = "AzureWebJobsSqlConnectionString") OutputBinding<Product> product,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        String json = request.getBody().get();
        product.setValue(new Gson().fromJson(json, Product.class));

        return request.createResponseBuilder(HttpStatus.OK).body(product).build();
    }

    @FunctionName("ProductsTrigger")
    public void ProductsTrigger(@SQLTrigger(name = "changes", tableName = "Products", connectionStringSetting = "AzureWebJobsSqlConnectionString") SqlChangeProduct[] changes,
            @SQLOutput(name = "product", commandText = "Products2", connectionStringSetting = "AzureWebJobsSqlConnectionString") OutputBinding<Product> product,
            final ExecutionContext context) {
        context.getLogger().info("Java SQL trigger function executed. Received row: " + new Gson().toJson(changes[0]));
        product.setValue(changes[0].Item);
    }

    public class Product {
        public int ProductId;
        public String Name;
        public int Cost;
    }

    public class SqlChangeProduct {
        public SqlChangeOperation Operation;
        public Product Item;
    }

    public enum SqlChangeOperation {
        Insert,
        Update,
        Delete
    }
}
