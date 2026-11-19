package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.google.gson.Gson;
import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.mysql.annotation.CommandType;
import com.microsoft.azure.functions.mysql.annotation.MySqlInput;
import com.microsoft.azure.functions.mysql.annotation.MySqlOutput;
import com.microsoft.azure.functions.mysql.annotation.MySqlTrigger;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Azure Functions with Azure MySql Database.
 */
public class MySqlTriggerTests {

    @FunctionName("GetProducts")
    public HttpResponseMessage GetProducts(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
            HttpMethod.POST }, route = "getproducts/{productid}", authLevel = AuthorizationLevel.ANONYMOUS)
            HttpRequestMessage<Optional<String>> request,
            @MySqlInput(name = "products", commandText = "SELECT TOP 1 * FROM Products WHERE ProductId = @ProductId",
            commandType = CommandType.Text, parameters = "@ProductId={productid}",
            connectionStringSetting = "AzureWebJobsMySqlConnectionString") Product[] products,
            final ExecutionContext context) {

        context.getLogger().info("Java HTTP trigger processed a MySql Input Binding request.");

        if (products.length != 0) {
            return request.createResponseBuilder(HttpStatus.OK).body(products[0].toString()).build();
        } else {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Did not find expected product in table Products").build();
        }
    }

    @FunctionName("AddProduct")
    public HttpResponseMessage AddProduct(@HttpTrigger(name = "req", methods = { HttpMethod.GET,
            HttpMethod.POST }, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            @MySqlOutput(name = "product", commandText = "Products", connectionStringSetting = "AzureWebJobsMySqlConnectionString") OutputBinding<Product> product,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a MySql Output Binding request.");

        String json = request.getBody().get();
        product.setValue(new Gson().fromJson(json, Product.class));

        return request.createResponseBuilder(HttpStatus.OK).body(product).build();
    }

    public class Product {
        public int ProductId;
        public String Name;
        public int Cost;

        public String toString() {
            return "{\"ProductId\":" + ProductId + ",\"Name\":\"" + Name + "\",\"Cost\":" + Cost + "}";
        }
    }
}
