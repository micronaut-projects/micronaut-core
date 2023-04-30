/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.server.tck.CorsUtils;
import io.micronaut.http.server.util.HttpHostResolver;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;
import static io.micronaut.http.tck.TestScenario.asserts;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import java.io.IOException;
import java.util.Collections;


@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public class CorsDisabledByDefaultTest {

    private static final String SPECNAME = "CorsDisabledByDefaultTest";

    /**
     * By default, CORS is disabled no cors headers are present in response.
     * @throws IOException may throw the try for resources
     */
    @Test
    void corsDisabledByDefault() throws IOException {
        asserts(SPECNAME,
            createRequest("https://foo.com"),
            (server, request) -> {
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(CorsUtils::assertCorsHeadersNotPresent)
                    .build());
            });
    }

    static HttpRequest<?> createRequest(String origin) {
        return HttpRequest.POST("/refresh", Collections.emptyMap())
            .header("Content-Type", MediaType.APPLICATION_JSON)
            .header("Origin", origin)
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .header("Accept", "*/*")
            .header("User-Agent", "Mozilla / 5.0 (Macintosh; Intel Mac OS X 10_15_7)AppleWebKit / 605.1 .15 (KHTML, like Gecko)Version / 16.1 Safari / 605.1 .15")
            .header("Referer", origin)
            .header("Accept-Language", "en - GB, en");
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller
    static class RefreshController {
        @Post("/refresh")
        @Status(HttpStatus.OK)
        void refresh() {
        }
    }

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
