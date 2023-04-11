package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class ExpressionTest {
    public static final String SPEC_NAME = "ExpressionTest";

    @Test
    void testConditionalGetRequest() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/expr/test"),
            (server, request) -> AssertionUtils.assertThrows(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build()));

        asserts(SPEC_NAME,
            HttpRequest.GET("/expr/test")
                .header(HttpHeaders.AUTHORIZATION, "foo"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("ok")
                    .build()));
    }

    @Controller("/expr")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ExpressionController {
        @Get(value = "/test", condition = "#{request.headers.getFirst('Authorization')?.contains('foo')}")
        String testGet() {
            return "ok";
        }
    }
}
