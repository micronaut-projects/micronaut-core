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
package io.micronaut.http.server.tck.tests.codec;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

import static io.micronaut.http.server.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class JsonCodeAdditionalTypeTest {
    public static final String SPEC_NAME = "JsonCodeAdditionalTypeTest";

    @Test
    void itIsPossibleToCanRegisterAdditionTypesForJsonCodec() throws IOException {
        asserts(SPEC_NAME,
            Collections.singletonMap("micronaut.codec.json.additional-types", Collections.singletonList("application/json+feed")),
            HttpRequest.GET("/json-additional-codec").header(HttpHeaders.ACCEPT, "application/json+feed"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, Argument.of(String.class), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .assertResponse(response -> {
                    Optional<String> jsonOptional = response.getBody(Argument.of(String.class));
                    assertTrue(jsonOptional.isPresent());
                    assertTrue(jsonOptional.get().contains("https://jsonfeed.org"));
                    assertEquals("application/json+feed", response.header("Content-Type"));
                }).build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller
    static class JsonFeedController {

        @Produces("application/json+feed")
        @Get("/json-additional-codec")
        String index() {
            return "{\n" +
                "    \"version\": \"https://jsonfeed.org/version/1\",\n" +
                "    \"title\": \"My Example Feed\",\n" +
                "    \"home_page_url\": \"https://example.org/\",\n" +
                "    \"feed_url\": \"https://example.org/feed.json\",\n" +
                "    \"items\": [\n" +
                "        {\n" +
                "            \"id\": \"2\",\n" +
                "            \"content_text\": \"This is a second item.\",\n" +
                "            \"url\": \"https://example.org/second-item\"\n" +
                "        },\n" +
                "        {\n" +
                "            \"id\": \"1\",\n" +
                "            \"content_html\": \"<p>Hello, world!</p>\",\n" +
                "            \"url\": \"https://example.org/initial-post\"\n" +
                "        }\n" +
                "    ]\n" +
                "}";
        }
    }
}
