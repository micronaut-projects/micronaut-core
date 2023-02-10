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
import java.net.URI;
import java.util.Collections;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.*;

public class DontFollowRedirectsTest {
    private static final String SPEC_NAME = "DisableRedirectTest";

    @Test
    void dontFollowRedirects() throws IOException {
        asserts(SPEC_NAME,
            Collections.singletonMap("micronaut.http.client.follow-redirects", StringUtils.FALSE),
            HttpRequest.GET("/redirect/redirect"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.SEE_OTHER)
                .assertResponse(response -> {
                    assertNotNull(response.getHeaders().get("Location"));
                    assertEquals("/redirect/direct", response.getHeaders().get("Location"));
                })
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class RedirectTestController {

        @Get("/redirect")
        HttpResponse<?> redirect() {
            return HttpResponse.seeOther(URI.create("/redirect/direct"));
        }
    }

}
