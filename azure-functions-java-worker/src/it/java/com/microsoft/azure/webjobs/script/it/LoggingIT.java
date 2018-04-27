package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;

import static io.restassured.RestAssured.*;

public class LoggingIT {
    private static RequestSpecification spec;

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test
    public void logging_user_mode_exception() {
        given().spec(spec)
                .when()
                .get("/loggingUserModeException")
                .then()
                .assertThat().statusCode(500);
    }
}
