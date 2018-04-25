package com.microsoft.azure.webjobs.script.integrationTest;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.*;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static io.restassured.RestAssured.*;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.assertj.core.api.Assertions.*;

public class HttpBinaryIT {

    private static RequestSpecification spec;

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .setBaseUri("http://localhost:7071")
                .addFilter(new ResponseLoggingFilter())//log request and response for better debugging. You can also only log if a requests fails.
                .addFilter(new RequestLoggingFilter())
                .build();
    }

    @Test
    public void test_hello() {
        given().spec(spec)
                .contentType("application/json")
                .queryParam("name", "Hello, World!")
                .when()
                .get("/api/hello")
                .then()
                .assertThat()
                .statusCode(200);
    }

    @Test
    public void primitive_byte_array_over_http() {
        String body = "Hello, World!";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(body.getBytes());

        given().spec(spec)
                .header("Content-Type", "application/octet-stream")
                .body(inputStream)
                .when()
                .post("/api/httpBinary")
                .then()
                .assertThat()
                .statusCode(200);

       // assertThat(responseBody).isEqualTo("Received " + body.length + " Bytes byte[] data");
    }
}
