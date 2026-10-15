package io.micronaut.http.server.tck.tests.binding;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class NullableQueryParamBindingTest {
    public static final String SPEC_NAME = "NullableQueryParamBindingTest";

    @Test
    void getQueryValueWithLocalDateTimeWithoutSeconds() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.GET("/nullablequeryparambinding"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .build()));
    }

    @Requires(property = "spec.name", value = "NullableQueryParamBindingTest")
    @Controller("/nullablequeryparambinding")
    static class NullableQueryParamBindingController {
        @Get
        HttpResponse<?> foo(@Nullable @QueryValue("foo") String bar) {
            return bar == null
                ? HttpResponse.ok()
                : HttpResponse.unprocessableEntity();
        }
    }
}
