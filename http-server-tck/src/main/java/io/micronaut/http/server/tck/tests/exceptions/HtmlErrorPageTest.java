/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.tck.tests.exceptions;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class HtmlErrorPageTest {
    private static final String SPEC_NAME = "HtmlErrorPageTest";

    @Test
    void htmlErrorPage() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.POST("/book/save", new Book("Building Microservices", "", 5000)).accept(MediaType.TEXT_HTML),
                (server, request) -> AssertionUtils.assertThrows(
                        server,
                        request,
                        HttpResponseAssertion.builder()
                                .status(HttpStatus.BAD_REQUEST)
                                .body(BodyAssertion.builder().body("<!doctype html>").contains())
                                .body(BodyAssertion.builder().body("book.author: must not be blank").contains())
                                .body(BodyAssertion.builder().body("book.pages: must be less than or equal to 4032").contains())
                                .headers(Map.of(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML))
                                .build()
                )
        );
    }

    @Requires(property = "spec.name", value = "HtmlErrorPageTest")
    @Controller("/book")
    static class FooController {

        @Produces(MediaType.TEXT_HTML)
        @Post("/save")
        @Status(HttpStatus.CREATED)
        void save(@Body @Valid Book book) {
            throw new UnsupportedOperationException();
        }
    }

    @Introspected
    record Book(@NotBlank String title, @NotBlank String author, @Max(4032) int pages) {

    }
}
