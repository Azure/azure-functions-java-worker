package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.*;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ContextIT {
    private static RequestSpecification spec;

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test
    public void context_function_name() {
        final String functionName = "contextFunctionName";
        final String expected = functionName;

        given().spec(spec)
                .when()
                .get("/" + functionName)
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }

    @Test
    public void context_invocation_id() {
        String actual = given().spec(spec)
                .when()
                .get("/contextInvocationId")
                .then()
                .assertThat().statusCode(200)
                .and().extract().body().asString();

        assertThat(actual).isNotNull();
    }
}
