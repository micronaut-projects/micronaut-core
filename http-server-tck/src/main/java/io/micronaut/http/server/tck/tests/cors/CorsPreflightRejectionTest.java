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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Options;
import io.micronaut.http.annotation.PathVariable;
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
 * A CORS preflight request from an origin that no CORS configuration allows is answered with 403
 * and does not reach routing.
 */
@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CorsPreflightRejectionTest {

    private static final String SPECNAME = "CorsPreflightRejectionTest";
    private static final String ALLOWED_ORIGIN = "https://foo.com";
    private static final String DISALLOWED_ORIGIN = "https://bar.com";
    private static final Map<String, Object> GLOBAL_CONFIG = Map.of(
        "micronaut.server.cors.enabled", StringUtils.TRUE,
        "micronaut.server.cors.configurations.foo.allowed-origins", List.of(ALLOWED_ORIGIN)
    );

    @Test
    void globalConfigurationAllowsPreflightFromAllowedOrigin() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            preflight("/preflight-global/item", ALLOWED_ORIGIN),
            allowed(ALLOWED_ORIGIN));
    }

    @Test
    void globalConfigurationRejectsPreflightFromDisallowedOrigin() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            preflight("/preflight-global/item", DISALLOWED_ORIGIN),
            forbidden());
    }

    @Test
    void crossOriginAllowsPreflightFromAllowedOrigin() throws IOException {
        asserts(SPECNAME,
            preflight("/preflight-annotated/item", ALLOWED_ORIGIN),
            allowed(ALLOWED_ORIGIN));
    }

    @Test
    void crossOriginRejectsPreflightFromDisallowedOrigin() throws IOException {
        asserts(SPECNAME,
            preflight("/preflight-annotated/item", DISALLOWED_ORIGIN),
            forbidden());
    }

    @Test
    void catchAllRouteDoesNotReceivePreflightFromDisallowedOrigin() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            preflight("/preflight-catchall/some/path", DISALLOWED_ORIGIN),
            (server, request) -> {
                forbidden().accept(server, request);
                assertEquals(0, server.getApplicationContext().getBean(CatchAllInvocations.class).count());
            });
    }

    @Test
    void preflightReachesRouteWhenCorsIsDisabled() throws IOException {
        asserts(SPECNAME,
            preflight("/preflight-catchall/some/path", DISALLOWED_ORIGIN),
            (server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("options some/path")
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
                assertEquals(1, server.getApplicationContext().getBean(CatchAllInvocations.class).count());
            });
    }

    @Test
    void nonPreflightOptionsRequestIsUnaffected() throws IOException {
        asserts(SPECNAME, GLOBAL_CONFIG,
            HttpRequest.OPTIONS("/preflight-catchall/some/path").header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN),
            (server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("options some/path")
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
                assertEquals(1, server.getApplicationContext().getBean(CatchAllInvocations.class).count());
            });
    }

    private static MutableHttpRequest<?> preflight(String path, String origin) {
        return HttpRequest.OPTIONS(path)
            .header(HttpHeaders.ORIGIN, origin)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET);
    }

    private static BiConsumer<ServerUnderTest, HttpRequest<?>> allowed(String origin) {
        return (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .assertResponse(response -> assertAllowed(response, origin))
            .build());
    }

    private static void assertAllowed(HttpResponse<?> response, String origin) {
        assertEquals(origin, response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertEquals(HttpMethod.GET.name(), response.getHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
    }

    private static BiConsumer<ServerUnderTest, HttpRequest<?>> forbidden() {
        return (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.FORBIDDEN)
            .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
            .build());
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/preflight-global")
    static class GlobalController {
        @Get("/item")
        String item() {
            return "item";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @CrossOrigin(ALLOWED_ORIGIN)
    @Controller("/preflight-annotated")
    static class AnnotatedController {
        @Get("/item")
        String item() {
            return "item";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/preflight-catchall")
    static class CatchAllController {
        private final CatchAllInvocations invocations;

        CatchAllController(CatchAllInvocations invocations) {
            this.invocations = invocations;
        }

        @Get("/{+path}")
        String get(@PathVariable String path) {
            invocations.increment();
            return "get " + path;
        }

        @Options("/{+path}")
        String options(@PathVariable String path) {
            invocations.increment();
            return "options " + path;
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Singleton
    static class CatchAllInvocations {
        private final AtomicInteger count = new AtomicInteger();

        void increment() {
            count.incrementAndGet();
        }

        int count() {
            return count.get();
        }
    }

    /**
     * The test server runs on localhost, so without a non-local host the drive-by-localhost
     * protection would reject every request from a remote origin before it reaches routing.
     */
    @Requires(property = "spec.name", value = SPECNAME)
    @Replaces(HttpHostResolver.class)
    @Singleton
    static class HttpHostResolverReplacement implements HttpHostResolver {
        @Override
        public String resolve(@Nullable HttpRequest request) {
            return "https://micronautexample.com";
        }
    }
}
