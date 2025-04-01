/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.cachecontrol.CacheControl;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.time.Duration;
import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class CacheControlTest {
    public static final String SPEC_NAME = "CacheControlTest";
    private static final HttpResponseAssertion ASSERTION = HttpResponseAssertion.builder()
        .status(HttpStatus.OK)
        .assertResponse(response -> {
            assertTrue(response.getHeaders().get(HttpHeaders.CACHE_CONTROL, String.class).isPresent());
            assertEquals("public, max-age=31536000, immutable", response.getHeaders().get(HttpHeaders.CACHE_CONTROL));
        }).build();

    @Test
    void canPopulateCacheControlHttpHeaderInResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/assets/bootstrap-5.3.3-dist/css/bootstrap.css").accept(MediaType.TEXT_CSS),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, ASSERTION));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class BootstrapController {
        @Produces(MediaType.TEXT_CSS)
        @Get("/assets/bootstrap-5.3.3-dist/css/bootstrap.css")
        String index() {
            return """
*,
*::before,
*::after {
  box-sizing: border-box;
""";
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @ServerFilter(patternStyle = FilterPatternStyle.REGEX, value = "^/assets/bootstrap.*$")
    static class PublicInmmutableCacheControlFilter {
        @ResponseFilter
        void filterResponse(MutableHttpResponse<?> rsp) {
            if (!rsp.getHeaders().contains(HttpHeaders.CACHE_CONTROL)) {
                rsp.cacheControl(
                    CacheControl.builder()
                        .publicDirective()
                        .maxAge(Duration.ofDays(365))
                        .inmutable()
                        .build());
            }
        }
    }

}
