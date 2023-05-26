package io.micronaut.http.server.tck.tests.mediatype;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.BodyAssertion;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.server.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class StringDefaultMediaTypeTest {
    public static final String SPEC_NAME = "StringDefaultMediaTypeTest";
    private static final HttpResponseAssertion ASSERTION = HttpResponseAssertion.builder()
        .status(HttpStatus.OK)
        .body(BodyAssertion.builder().body("Hello World").equals())
        .assertResponse(response -> {
            assertTrue(response.getContentType().isPresent());
            assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getContentType().get());
        }).build();

    @Test
    void jsonIsDefaultMediaTypeForString() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/str"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ASSERTION));
    }

    @Controller("/str")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class StrDefaultEncoding {
        @Get
        String index() {
            return "Hello World";
        }
    }
}
