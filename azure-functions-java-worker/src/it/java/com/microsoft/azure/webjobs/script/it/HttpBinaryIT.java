package com.microsoft.azure.webjobs.script.it;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.*;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;
import java.util.Arrays;

import static io.restassured.RestAssured.*;
import static org.assertj.core.api.Assertions.*;

public class HttpBinaryIT {

    private static RequestSpecification spec;
    private static final String content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras metus justo, dictum vel tortor nullam.";
    private static final byte[] contentBytes = content.getBytes();

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .setBaseUri("http://localhost:7071")
                .setContentType(ContentType.TEXT)
                .setBody(Arrays.toString(contentBytes))
                .addFilter(new ResponseLoggingFilter())//log request and response for better debugging. You can also only log if a requests fails.
                .addFilter(new RequestLoggingFilter())
                .build();
    }

    @Test
    public void primitive_byte_array_over_http() {
        String responseBody = given()
                .spec(spec)
                .when()
                .post("/api/primitiveByteArray")
                .then()
                .assertThat().statusCode(200).and().extract().body().asString();

        assertThat(responseBody).isEqualTo("Received " + contentBytes.length + " bytes byte[] data");
    }

    @Test
    public void class_byte_array_over_http() {
        byte[] bytes = content.getBytes();

        String responseBody = given()
                .spec(spec)
                .when()
                .post("/api/classByteArray")
                .then()
                .assertThat().statusCode(200).and().extract().body().asString();

       assertThat(responseBody).isEqualTo("Received " + contentBytes.length + " bytes Byte[] data");
    }
}
