package com.microsoft.azure.functions.endtoend;

import com.microsoft.azure.functions.annotation.*;
import com.microsoft.azure.functions.*;

import java.awt.*;
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

    @FunctionName("HttpTriggerJavaClassLoader")
    public HttpResponseMessage HttpTriggerJavaClassLoader(
            @HttpTrigger(name = "req", methods = {HttpMethod.GET, HttpMethod.POST}, authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) throws Exception {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameters
        String query = request.getQueryParameters().get("name");
        String name = request.getBody().orElse(query);

        //Make sure there is no class not found excption.
        new Gson();

        if(!SystemUtils.IS_JAVA_15) {
            context.getLogger().info("Java version not 15");
        }

        if (name == null ) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        }
        return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
    }

    public static int count = 1;
    public static int countExp = 1;
    public static int countFail = 1;
    public static int countExpFail = 1;

    //TODO: write new cases for replacement
//    @FunctionName("HttpExample-runRetryFail")
//    @FixedDelayRetry(maxRetryCount = 3, delayInterval = "00:00:05")
//    public HttpResponseMessage runRetryFail(
//            @HttpTrigger(
//                    name = "req",
//                    methods = {HttpMethod.GET, HttpMethod.POST},
//                    authLevel = AuthorizationLevel.ANONYMOUS)
//                    HttpRequestMessage<Optional<String>> request,
//            final ExecutionContext context) throws Exception {
//        context.getLogger().info("Java HTTP trigger processed a request.");
//
//        if(countFail<2) {
//            throw new Exception("error");
//        }
//
//        // Parse query parameter
//        final String query = request.getQueryParameters().get("name");
//        final String name = request.getBody().orElse(query);
//
//        if (name == null) {
//            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
//        } else {
//            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
//        }
//    }

    //TODO: write new cases for replacement
//    @FunctionName("HttpExample-runExponentialBackoffRetryFail")
//    @ExponentialBackoffRetry(maxRetryCount = 3, minimumInterval = "00:00:01", maximumInterval = "00:00:03")
//    public HttpResponseMessage runRetryExponentialBackoffRetryFail(
//            @HttpTrigger(
//                    name = "req",
//                    methods = {HttpMethod.GET, HttpMethod.POST},
//                    authLevel = AuthorizationLevel.ANONYMOUS)
//                    HttpRequestMessage<Optional<String>> request,
//            final ExecutionContext context) throws Exception {
//        context.getLogger().info("Java HTTP trigger processed a request.");
//
//        if(countExpFail<2) {
//            throw new Exception("error");
//        }
//
//        // Parse query parameter
//        final String query = request.getQueryParameters().get("name");
//        final String name = request.getBody().orElse(query);
//
//        if (name == null) {
//            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
//        } else {
//            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
//        }
//    }

    private static int flag = 0;

    @FunctionName("HttpTriggerJavaStatic1")
    public HttpResponseMessage HttpTriggerJavaStatic1(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger - static processed a request.");
        flag++;
        return request.createResponseBuilder(HttpStatus.OK).body(String.valueOf(flag)).build();
    }

    @FunctionName("HttpTriggerJavaStatic2")
    public HttpResponseMessage HttpTriggerJavaStatic2(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger - static processed a request.");
        flag++;
        return request.createResponseBuilder(HttpStatus.OK).body(String.valueOf(flag)).build();
    }

    @FunctionName("HttpTriggerWaitMethod")
    public HttpResponseMessage wait(
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
            return request.createResponseBuilder(HttpStatus.OK).body(name).build();
        }
    }

    @FunctionName("HttpTriggerNotifyMethod")
    public HttpResponseMessage notify(
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
            return request.createResponseBuilder(HttpStatus.OK).body(name).build();
        }
    }

    @FunctionName("HttpTriggerJavaVersion")
    public static HttpResponseMessage HttpTriggerJavaVersion(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context
    ) {
        context.getLogger().info("Java HTTP trigger processed a request.");
        final String javaVersion = getJavaVersion();
        context.getLogger().info("Function - HttpTriggerJavaVersion" + javaVersion);
        return request.createResponseBuilder(HttpStatus.OK).body("HttpTriggerJavaVersion").build();
    }

    public static String getJavaVersion() {
        return String.join(" - ", System.getProperty("java.home"), System.getProperty("java.version"));
    }

    //Issue Fix: https://github.com/Azure/azure-functions-docker/pull/668
    @FunctionName("FontTypeSupport")
    public HttpResponseMessage FontTypeSupport(
            @HttpTrigger(
                    name = "req",
                    methods = {HttpMethod.GET, HttpMethod.POST},
                    authLevel = AuthorizationLevel.ANONYMOUS)
                    HttpRequestMessage<Optional<String>> request,
            final ExecutionContext context) {
        context.getLogger().info("Java HTTP trigger processed a request.");

        // Parse query parameter
        final String query = request.getQueryParameters().get("name");
        final String name = request.getBody().orElse(query);

        String[] libNames = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        StringBuilder sb = new StringBuilder();
        for (String libName : libNames) {
            sb.append(libName).append("--");
        }
        context.getLogger().info("Font libs are: " + sb);

        if (name == null) {
            return request.createResponseBuilder(HttpStatus.BAD_REQUEST).body("Please pass a name on the query string or in the request body").build();
        } else {
            return request.createResponseBuilder(HttpStatus.OK).body("Hello, " + name).build();
        }
    }
}
