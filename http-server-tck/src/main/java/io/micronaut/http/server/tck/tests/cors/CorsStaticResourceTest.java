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
package io.micronaut.http.server.tck.tests.cors;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CorsStaticResourceTest {
    public static final String SPEC_NAME = "CorsStaticResourceTest";
    public static final String ORIGIN = "http://some.domain.com";

    @Test
    public void staticResource() throws IOException {
        Map<String, Object> config = Map.of(
            "micronaut.server.cors.enabled", StringUtils.TRUE,
            "micronaut.server.cors.configurations.default.allowed-origins", ORIGIN,
            "micronaut.server.cors.configurations.default.allowed-methods[0]", "GET",
            "micronaut.router.static-resources.assets.mapping", "/assets/**",
            "micronaut.router.static-resources.assets.paths", "classpath:assets");
        HttpRequest<?> request = HttpRequest.OPTIONS(UriBuilder.of("/assets").path("hello.txt").build())
            .accept(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
        Map<String, String> expectedHeaders = Map.of(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, HttpMethod.GET.name(),
            HttpHeaders.ACCESS_CONTROL_MAX_AGE, "1800",
            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN,
            HttpHeaders.VARY, "Origin");
        asserts(SPEC_NAME, config, request,
            (server, req) ->
                AssertionUtils.assertDoesNotThrow(server, req, HttpStatus.OK, null, expectedHeaders));
        request = HttpRequest.OPTIONS(UriBuilder.of("/assets").path("nonexisiting.txt").build())
            .accept(MediaType.TEXT_PLAIN)
            .header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.GET.name());
        asserts(SPEC_NAME, config, request,
            (server, req) ->
                AssertionUtils.assertThrowsStatus(HttpStatus.FORBIDDEN));
    }
}
