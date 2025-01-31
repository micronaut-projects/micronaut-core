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
package io.micronaut.http.server.tck.tests.forms;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.TestScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FormBindingUsingMethodParametersTest {
    private static final String SPEC_NAME = "FormBindingUsingMethodParametersTest";

    @Test
    public void formBindingUsingMethodParameters() throws IOException {
        String body = "title=Building+Microservices&pages=100";
        String expectedJson = "{\"title\":\"Building Microservices\",\"pages\":100}";
        assertWithBody(body, expectedJson);

        body = "title=Building+Microservices&pages=";
        expectedJson = "{\"title\":\"Building Microservices\"}";
        assertWithBody(body, expectedJson);
    }

    private static void assertWithBody(String body, String expectedJson) throws IOException {
        TestScenario.builder()
            .specName(SPEC_NAME)
            .request(HttpRequest.POST("/book/save", body).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE))
            .assertion((server, request) ->
                AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .assertResponse(httpResponse -> {
                        Optional<String> bodyOptional = httpResponse.getBody(String.class);
                        assertTrue(bodyOptional.isPresent());
                        assertEquals(expectedJson, bodyOptional.get());
                    })
                    .build()))
            .run();
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/book")
    static class SaveController {
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/save")
        Book save(String title, @Nullable Integer pages) {
            return new Book(title, pages);
        }
    }

    @Introspected
    record Book(@NonNull String title, @Nullable Integer pages) {
    }
}
