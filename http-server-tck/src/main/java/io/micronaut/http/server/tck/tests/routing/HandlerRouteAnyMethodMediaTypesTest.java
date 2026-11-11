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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * A request of any method, standard or custom, that a route of
 * {@link HttpRouteBuilder#any(String)} rejects for its media types is answered with
 * {@code 415} or {@code 406}, not with {@code 405}.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteAnyMethodMediaTypesTest {
    public static final String SPEC_NAME = "HandlerRouteAnyMethodMediaTypesTest";

    @Test
    void anUnsupportedContentTypeIs415ForACustomMethod() throws IOException {
        assertStatus(request(HttpMethod.CUSTOM, "REPORT").contentType(MediaType.TEXT_PLAIN_TYPE), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void anUnacceptableAcceptIs406ForACustomMethod() throws IOException {
        assertStatus(request(HttpMethod.CUSTOM, "REPORT").contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN), HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void aStandardMethodIsAnsweredTheSame() throws IOException {
        assertStatus(request(HttpMethod.POST, "POST").contentType(MediaType.TEXT_PLAIN_TYPE), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertStatus(request(HttpMethod.POST, "POST").contentType(MediaType.APPLICATION_JSON_TYPE)
            .header(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN), HttpStatus.NOT_ACCEPTABLE);
    }

    @Test
    void anAcceptedRequestIsRouted() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, request(HttpMethod.CUSTOM, "REPORT").contentType(MediaType.APPLICATION_JSON_TYPE),
                HttpResponseAssertion.builder().status(HttpStatus.OK).body("{\"method\":\"REPORT\"}").build());
        }
    }

    private static MutableHttpRequest<String> request(HttpMethod method, String name) {
        return HttpRequest.<String>create(method, "/any-media", name).body("{}");
    }

    private static void assertStatus(HttpRequest<?> request, HttpStatus status) throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder().status(status).build());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class AnyMediaRoutes implements HttpRoutes {
        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.any("/any-media")
                .consumes(MediaType.APPLICATION_JSON_TYPE)
                .produces(MediaType.APPLICATION_JSON_TYPE)
                .handle((request, pathVariables) -> HttpResponse.ok("{\"method\":\"" + request.getMethodName() + "\"}")
                    .contentType(MediaType.APPLICATION_JSON_TYPE));
        }
    }
}
