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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CrossOriginPolicyTest {
    private static final String SPEC_NAME = "CrossOriginPolicyTest";
    private static final Map<String, Object> CONFIGURATION = Map.of(
        "micronaut.server.cors.enabled", StringUtils.FALSE,
        "micronaut.server.cors.cross-origin-embedder-policy", "require-corp",
        "micronaut.server.cors.cross-origin-resource-policy", "same-site"
    );

    @Test
    void configuredCrossOriginPoliciesAreIncludedInResponses() throws IOException {
        asserts(SPEC_NAME,
            CONFIGURATION,
            HttpRequest.GET("/cross-origin-policy"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("require-corp", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY));
                    assertEquals("same-site", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY));
                })
                .build()));
    }

    @Test
    void configuredCrossOriginPoliciesDoNotOverwriteExistingResponseHeaders() throws IOException {
        asserts(SPEC_NAME,
            CONFIGURATION,
            HttpRequest.GET("/cross-origin-policy-with-headers"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    assertEquals("unsafe-none", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY));
                    assertEquals("same-origin", response.getHeaders().get(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY));
                })
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class CrossOriginPolicyController {
        @Get("/cross-origin-policy")
        String index() {
            return "ok";
        }

        @Get("/cross-origin-policy-with-headers")
        HttpResponse<?> indexWithHeaders() {
            return HttpResponse.ok("ok")
                .header(HttpHeaders.CROSS_ORIGIN_EMBEDDER_POLICY, "unsafe-none")
                .header(HttpHeaders.CROSS_ORIGIN_RESOURCE_POLICY, "same-origin");
        }
    }
}
