package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
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
    public static void initSpec() {
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.TEXT)
                .setBody(Arrays.toString(contentBytes))
                .build();
    }

    @Test
    public void primitive_byte_array_over_http() {
        String responseBody = given()
                .spec(spec)
                .when()
                .post("/primitiveByteArray")
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
                .post("/classByteArray")
                .then()
                .assertThat().statusCode(200).and().extract().body().asString();

       assertThat(responseBody).isEqualTo("Received " + contentBytes.length + " bytes Byte[] data");
    }
}
