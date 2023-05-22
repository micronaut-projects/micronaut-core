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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static io.micronaut.http.tck.ServerUnderTest.BLOCKING_CLIENT_PROPERTY;
import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DontFollowRedirectsTest {

    private static final String SPEC_NAME = "DisableRedirectTest";

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    void dontFollowRedirects(boolean blocking) throws IOException {
        asserts(SPEC_NAME,
            Map.of(
                "micronaut.http.client.follow-redirects", StringUtils.FALSE,
                BLOCKING_CLIENT_PROPERTY, blocking
            ),
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
