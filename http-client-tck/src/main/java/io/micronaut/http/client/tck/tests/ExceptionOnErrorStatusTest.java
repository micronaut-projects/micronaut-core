package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;

public class ExceptionOnErrorStatusTest {

    private static final String SPEC_NAME = "ExceptionOnErrorStatusTest";

    @Test
    void exceptionOnErrorStatus() throws IOException {
        asserts(SPEC_NAME,
            Collections.singletonMap("micronaut.http.client.exception-on-error-status", StringUtils.FALSE),
            HttpRequest.GET("/unprocessable"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("{\"message\":\"Cannot make it\"}")
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/unprocessable")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class RedirectTestController {

        @Get
        HttpResponse<?> index() {
            return HttpResponse.unprocessableEntity().body("{\"message\":\"Cannot make it\"}");
        }
    }
}
