package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;
import java.util.*;

import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import java.io.InputStream;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.lang3.SystemUtils;

/**
 * Azure Functions with HTTP trigger.
 */
public class HttpTriggerTests {
    /**
     * This function will listen at HTTP endpoint "/api/HttpTrigger".
     */
    @FunctionName("HttpTriggerJava")
    public HttpResponseMessage HttpTriggerJava(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) throws Exception {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);
        String readEnv = System.getenv("AzureWebJobsStorage");

        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:test")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("select 1");
            }
        }

        Gson a = new Gson();

//        if(!SystemUtils.IS_JAVA_15) {
//            context.getLogger().info("Java version not 15");
//        }

        get("https://httpstat.us/200");

        if (name == null ) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        }
        if (readEnv == null ) {
            return request.createResponseBuilder(HttpStatus.INTERNAL_SERVER_ERROR).body("AzureWebJobsStorage is empty").build();
        } 
        return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
    }

    private static String get(String url) throws IOException {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("Accept", "application/json");
        HttpResponse response = httpClient.execute(httpGet);
        InputStream content = response.getEntity().getContent();
        String body = CharStreams.toString(new InputStreamReader(content));
        content.close();
        httpClient.close();
        return "Response from " + url + " was: " + body;
    }

    @FunctionName("HttpTriggerJavaThrows")
    public HttpResponseMessage HttpTriggerThrows(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context)  throws Exception{
        context.getLogger().info("Java HTTP trigger processed a request.");
        throw new Exception("Test Exception");
    }   
    
    @FunctionName("HttpTriggerJavaMetadata")
    public static String HttpTriggerJavaMetadata(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        @BindingName("firstName") String queryValue1, @BindingName("lastName") String queryValue2
    ) {
        return queryValue1+queryValue2;
    }

    @FunctionName("HttpTriggerCustomCode")
    public HttpResponseMessage HttpTriggerCustomCode(
        @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
        final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatusType.custom(209)).body("Hello, " + name).build();
        }
    }
}
