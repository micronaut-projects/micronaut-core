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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.uri.UriBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class RequestUriTest {

    public static final String SPEC_NAME = "RequestUriTest";

    @Test
    void testRequestUriContainsQueryValue() throws IOException {
        URI uri = UriBuilder.of("/requesturi")
            .queryParam("A", "foo")
            .queryParam("B", "bar")
            .build();
        asserts(SPEC_NAME,
            HttpRequest.GET(uri),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(httpResponse -> {
                        Optional<String> resultOptional = httpResponse.getBody(String.class);
                        assertTrue(resultOptional.isPresent());
                        String result = resultOptional.get();
                        assertNotNull(result);
                        assertEquals(1, countOcurrences(result, "A=foo"));
                        assertEquals(1, countOcurrences(result, "B=bar"));
                    })
                    .build()));
    }

    private static int countOcurrences(String str, String findStr) {
        int lastIndex = 0;
        int count = 0;
        while (lastIndex != -1) {

            lastIndex = str.indexOf(findStr, lastIndex);

            if (lastIndex != -1) {
                count++;
                lastIndex += findStr.length();
            }
        }
        return count;
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/requesturi")
    static class TestController {
        @Get
        String index(HttpRequest<?> request) {
            return request.getUri().toASCIIString();
        }
    }
}
