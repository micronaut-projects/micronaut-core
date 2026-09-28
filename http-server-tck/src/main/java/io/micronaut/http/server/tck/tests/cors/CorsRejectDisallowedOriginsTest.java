/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.tests.cors;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.http.server.tck.CorsUtils;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With {@code micronaut.server.cors.reject-disallowed-origins} a cross-origin request from an origin that
 * no CORS configuration allows is answered with 403, while same-origin requests pass.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CorsRejectDisallowedOriginsTest {

    private static final String SPECNAME = "CorsRejectDisallowedOriginsTest";
    private static final String SERVER_ORIGIN = "https://micronautexample.com";
    private static final String ALLOWED_ORIGIN = "https://foo.com";
    private static final String DISALLOWED_ORIGIN = "https://bar.com";
    private static final String REJECT = "micronaut.server.cors.reject-disallowed-origins";
    private static final Map<String, Object> GLOBAL_CONFIG = Map.of(
        "micronaut.server.cors.enabled", StringUtils.TRUE,
        "micronaut.server.cors.configurations.foo.allowed-origins", List.of(ALLOWED_ORIGIN),
        REJECT, StringUtils.TRUE
    );

    @Test
    void requestFromDisallowedOriginIsRejected() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            get("/reject-global", DISALLOWED_ORIGIN),
            (server, request) -> {
                AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
                assertEquals(0, invocations(server));
            });
    }

    @Test
    void requestFromAllowedOriginIsServed() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            get("/reject-global", ALLOWED_ORIGIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("global")
                .assertResponse(response -> assertEquals(ALLOWED_ORIGIN, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)))
                .build()));
    }

    @Test
    void sameOriginRequestIsServed() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            get("/reject-global", SERVER_ORIGIN),
            ok("global"));
    }

    @Test
    void sameOriginRequestWithExplicitDefaultPortIsServed() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            get("/reject-global", SERVER_ORIGIN + ":443"),
            ok("global"));
    }

    @Test
    void requestFromDisallowedOriginIsServedByDefault() throws IOException {
        asserts(SPECNAME, Map.of(
                "micronaut.server.cors.enabled", StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins", List.of(ALLOWED_ORIGIN)),
            get("/reject-global", DISALLOWED_ORIGIN),
            ok("global"));
    }

    @Test
    void crossOriginRouteRejectsDisallowedOrigin() throws IOException {
        asserts(SPECNAME, Map.of(REJECT, StringUtils.TRUE),
            get("/reject-annotated", DISALLOWED_ORIGIN),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build()));
    }

    @Test
    void routeWithoutCrossOriginIsServedWhenCorsIsDisabled() throws IOException {
        asserts(SPECNAME, Map.of(REJECT, StringUtils.TRUE),
            get("/reject-global", DISALLOWED_ORIGIN),
            ok("global"));
    }

    private static HttpRequest<?> get(String path, String origin) {
        return HttpRequest.GET(path).header(HttpHeaders.ORIGIN, origin);
    }

    private static BiConsumer<ServerUnderTest, HttpRequest<?>> ok(String body) {
        return (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .body(body)
            .build());
    }

    private static int invocations(ServerUnderTest server) {
        return server.getApplicationContext().getBean(Invocations.class).count.get();
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller
    static class RejectController {
        private final Invocations invocations;

        RejectController(Invocations invocations) {
            this.invocations = invocations;
        }

        @Get("/reject-global")
        String global() {
            invocations.count.incrementAndGet();
            return "global";
        }

        @CrossOrigin(ALLOWED_ORIGIN)
        @Get("/reject-annotated")
        String annotated() {
            invocations.count.incrementAndGet();
            return "annotated";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Singleton
    static class Invocations {
        private final AtomicInteger count = new AtomicInteger();
    }

    /**
     * Gives the server a non-local origin, so the drive-by-localhost protection stays out of the way
     * and same-origin requests can be expressed.
     */
    @Requires(property = "spec.name", value = SPECNAME)
    @Replaces(HttpHostResolver.class)
    @Singleton
    static class HttpHostResolverReplacement implements HttpHostResolver {
        @Override
        public String resolve(@Nullable HttpRequest request) {
            return SERVER_ORIGIN;
        }
    }
}
