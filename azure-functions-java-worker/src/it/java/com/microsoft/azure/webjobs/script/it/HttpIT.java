package com.microsoft.azure.webjobs.script.it;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class HttpIT {
    private static RequestSpecification spec;
    private static final String content = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Cras metus justo, dictum vel tortor nullam.";

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .setBaseUri("http://localhost:7071/api/")
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test
    public void http_handle_same_name() {
        String value = "value";

        given().spec(spec)
            .queryParam("req", value)
            .when()
            .get("httpsamename")
            .then()
            .assertThat().body(equalTo(value));
    }
}
