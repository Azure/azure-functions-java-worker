package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.functions.HttpFunction;
import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.extractProperty;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.assertj.core.api.Assertions.filter;
import static org.assertj.core.api.Assertions.setAllowExtractingPrivateFields;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.Index.atIndex;
import static org.assertj.core.util.Arrays.array;
import static org.assertj.core.util.Lists.newArrayList;

import org.junit.*;

import java.lang.reflect.Array;
import java.util.Arrays;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

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
