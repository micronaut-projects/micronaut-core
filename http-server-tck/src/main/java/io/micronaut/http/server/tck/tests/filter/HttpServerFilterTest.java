/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.tck.tests.filter;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.TestScenario;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import jakarta.annotation.security.RolesAllowed;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HttpServerFilterTest {
    private static final String PATH = "/http-server-filter-test";
    private static final String SPEC_NAME = "HttpServerFilterTest";

    @Test
    public void httpServerFilterTest() throws IOException {
        assertion(HttpRequest.GET(PATH),
            throwsStatus(HttpStatus.UNAUTHORIZED));

        assertion(HttpRequest.GET(PATH).header(HttpHeaders.AUTHORIZATION, "ROLE_USER"),
            throwsStatus(HttpStatus.FORBIDDEN));

        BiConsumer<ServerUnderTest, HttpRequest<?>> okAssertion = (server, request) ->
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("foo")
                .build());

        assertion(HttpRequest.GET(PATH).header(HttpHeaders.AUTHORIZATION, "ROLE_ADMIN"),
            okAssertion);

        assertion(HttpRequest.GET("/open"),
            okAssertion);
    }

    private static BiConsumer<ServerUnderTest, HttpRequest<?>> throwsStatus(HttpStatus status) {
        return (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
            .status(status)
            .build());
    }

    private static void assertion(HttpRequest<?> request, BiConsumer<ServerUnderTest, HttpRequest<?>> assertion) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(request)
            .assertion(assertion)
            .run();
    }

    @Filter(value = MATCH_ALL_PATTERN)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SecurityFilter implements HttpServerFilter {

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            RouteMatch<?> routeMatch = RouteAttributes.getRouteMatch(request).orElse(null);
            if (routeMatch instanceof MethodBasedRouteMatch match) {
                MethodBasedRouteMatch<?, ?> methodRoute = match;
                if (methodRoute.hasAnnotation(RolesAllowed.class)) {
                    String role = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
                    if (role == null) {
                        return Mono.fromCallable(() -> HttpResponse.status(HttpStatus.UNAUTHORIZED));
                    }
                    Optional<String[]> optionalValue = methodRoute.getValue(RolesAllowed.class, String[].class);
                    if (optionalValue.isPresent()) {
                        String[] roles = optionalValue.get();
                        if (role != null && Stream.of(roles).anyMatch(r -> r.equals(role))) {
                            return chain.proceed(request);
                        }
                    }
                    return Mono.fromCallable(() -> HttpResponse.status(HttpStatus.FORBIDDEN));
                }
            }
            return chain.proceed(request);
        }
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    public static class MyController {
        @RolesAllowed("ROLE_ADMIN")
        @Get("/http-server-filter-test")
        public String rolesAllowed(HttpRequest<?> request) {
            return "foo";
        }

        @Get("/open")
        public String open(HttpRequest<?> request) {
            return "foo";
        }
    }

}
