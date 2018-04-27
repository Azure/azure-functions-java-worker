package com.microsoft.azure.webjobs.script.it;

import com.microsoft.azure.webjobs.script.it.functions.dto.Point;
import com.microsoft.azure.webjobs.script.it.utils.RequestSpecificationProvider;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import static org.assertj.core.api.Assertions.*;

import org.junit.*;
import java.util.Arrays;

import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class HttpIT {
    private static RequestSpecification spec;

    @BeforeClass
    public static void initSpec(){
        spec = new RequestSpecBuilder()
                .addRequestSpecification(RequestSpecificationProvider.getDefault())
                .setContentType(ContentType.JSON)
                .build();
    }

    @Test
    public void http_echo_body() {
        String value = "value";

        given().spec(spec)
                .body(value)
                .when()
                .post("/httpEcho")
                .then()
                .assertThat().statusCode(202)
                .and().body(equalTo(value));
    }

    @Test
    public void http_echo_query_param() {
        String value = "value";

        given().spec(spec)
                .queryParam("name", value)
                .when()
                .get("/httpEcho")
                .then()
                .assertThat().body(equalTo("Hello " + value + "!"));
    }

    @Test
    public void http_handle_same_name() {
        String value = "value";

        given().spec(spec)
            .queryParam("req", value)
            .when()
            .get("/httpSameName")
            .then()
            .assertThat().body(equalTo(value));
    }

    @Test
    public void http_handle_string() {
        String value = "lorem ipsum et al";
        String expected = "HttpFunction string content \"" + value + "\"!";

        given().spec(spec)
                .body(value)
                .when()
                .post("/httpHandleString")
                .then()
                .assertThat().body(equalTo(expected)).and().statusCode(280);
    }

    @Test
    public void http_handle_int() {
        int value = 10;
        String expected = Integer.toString(value + 111);

        given().spec(spec)
            .body(value)
            .when()
            .post("/httpHandleInt")
            .then()
            .assertThat().body(equalTo(expected)).and().statusCode(281);
    }

    @Test
    public void http_handle_int_array() {
        int[] value = new int[] { 10, 11, 12, 13, 14, 15 };
        int[] expected = Arrays.stream(value).map(i -> i + 222).toArray();

        int[] actual = given().spec(spec)
                .body(value)
                .when()
                .post("/httpHandleIntArray")
                .then()
                .statusCode(282)
                .extract().body().as(int[].class);

        assertThat(actual).containsExactly(expected);
    }

    @Test
    public void http_handle_pojo() {
        Point value = new Point(55, 66);
        Point expected = new Point(value.getX() + 333, value.getY() + 333);

        Point actual = given().spec(spec)
                .body(value)
                .when()
                .post("/httpHandlePojo")
                .then()
                .statusCode(283)
                .extract().body().as(Point.class);

        assertThat(actual.getX()).isEqualTo(expected.getX());
        assertThat(actual.getY()).isEqualTo(expected.getY());
    }

    @Test
    public void http_handle_pojo_array() {
        Point[] value = new Point[] {
                new Point(77, 88),
                new Point(99, 100)
        };

        Point[] expected = Arrays.stream(value).map(p ->
                new Point(p.getX() + 444, p.getY() + 444)
            ).toArray(Point[]::new);

        Point[] actual = given().spec(spec)
                .body(value)
                .when()
                .post("/httpHandlePojoArray")
                .then()
                .statusCode(284)
                .extract().body().as(Point[].class);

        assertThat(extractProperty("x").from(actual)).contains(expected[0].getX(), expected[1].getX());
        assertThat(extractProperty("y").from(actual)).contains(expected[0].getY(), expected[1].getY());
    }

    @Test
    public void http_handle_legacy() {
        String value = "Http Request String Body (Legacy)";

        given().spec(spec)
                .header("Content-Type", "text/plain")
                .body(value)
                .when()
                .post("/httpHandleLegacy")
                .then()
                .assertThat().contentType("")
                .and().statusCode(285)
                .and().body(equalTo(value));
    }

    @Test
    public void http_handle_headers() {
        given().spec(spec)
                .when()
                .body("")
                .get("/httpHandleHeaders")
                .then()
                .assertThat().statusCode(286)
                .and().header("test-header", equalTo("test response header value"))
                .and().body(equalTo("Check header value"));
    }

    @Test
    public void http_post_optional_body_as_null() {
        given().spec(spec)
                .when()
                .get("/httpOptionalBody")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo("There is no optional content"));
    }

    @Test
    public void http_post_optional_body_with_value() {
        final String value = "";
        final String expected = "There is no optional content";

        given().spec(spec)
                .when()
                .body(value)
                .get("/httpOptionalBody")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }

    @Test
    public void http_post_null_body_to_non_optional_String() {
        given().spec(spec)
                .when()
                .post("/httpNullBody")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo("HttpFunction body string is null"));
    }

    @Test
    public void http_post_non_null_body_to_verify_null_is_handled() {
        final String value = "verify non-null body";
        final String expected = "Nice! The http body string is \"" + value + "\"";

        given().spec(spec)
                .when()
                .body(value)
                .post("/httpNullBody")
                .then()
                .assertThat().statusCode(200)
                .and().body(equalTo(expected));
    }
}
