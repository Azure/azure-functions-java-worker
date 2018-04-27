package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.functions.dto.*;
import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.*;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class OverloadIT {
    private static RequestSpecification spec;

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test
    public void overload_method_call_one() {
        OverloadPojo1 value = new OverloadPojo1();
        value.number = 1;

        final String expected = "Overload " + value.number;

        given().spec(spec)
                .body(value)
                .when()
                .get("/overloadMethodOne")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }

    @Test
    public void overload_method_call_two() {
        OverloadPojo2 value = new OverloadPojo2();
        value.number = 2;

        final String expected = "Overload " + value.number;

        given().spec(spec)
                .body(value)
                .when()
                .get("/overloadMethodTwo")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }

    @Test
    public void overload_method_with_param() {
        final String expected = "This is correct method for HttpBinding resolution";

        given().spec(spec)
                .queryParam("req", "value doesn't matter")
                .when()
                .get("/overloadBindingName")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }

    /**
     * This is confirming that the other overloaded method DOES NOT get called.
     * The expected result should be the same as the test overload_method_with_param.
     */
    @Test
    public void overload_method_without_param() {
        final String expected = "This is correct method for HttpBinding resolution";

        given().spec(spec)
                .when()
                .get("/overloadBindingName")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }
}
