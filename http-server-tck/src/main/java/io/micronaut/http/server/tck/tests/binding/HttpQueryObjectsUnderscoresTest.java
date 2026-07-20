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
package io.micronaut.http.server.tck.tests.binding;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HttpQueryObjectsUnderscoresTest {
    public static final String SPEC_NAME = "HttpQueryObjectsUnderscoresTest";

    @Test
    void testQueryObjectUnderscores() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/query-object-underscores/query-object?title=JavaBook&extra_details=Details&other_property=SomeValue"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Parameter Value: JavaBook Details SomeValue")
                .build()));
    }

    @Test
    void testQueryObjectUnderscoresAbsentAndNullable() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/query-object-underscores/query-object?title=JavaBook"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Parameter Value: JavaBook null null")
                .build()));
    }

    @Test
    void testQueryObjectAliasPrecedence() throws IOException {
        asserts(SPEC_NAME,
            // When both extra_details (alias) and extraDetails (Java property name) are present,
            // the alias (extra_details) must take precedence.
            HttpRequest.GET("/query-object-underscores/query-object?title=JavaBook&extra_details=AliasValue&extraDetails=JavaPropValue"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Parameter Value: JavaBook AliasValue null")
                .build()));
    }

    @Test
    void testQueryObjectJavaPropertyFallback() throws IOException {
        asserts(SPEC_NAME,
            // When only extraDetails (Java property name) is present instead of extra_details (alias),
            // the binder must fall back to matching the Java property name.
            HttpRequest.GET("/query-object-underscores/query-object?title=JavaBook&extraDetails=JavaPropValue"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("Parameter Value: JavaBook JavaPropValue null")
                .build()));
    }

    @Controller("/query-object-underscores")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class QueryObjectUnderscoresController {

        @Get("/query-object")
        String queryObject(@QueryValue Book book) {
            return "Parameter Value: " + book.getTitle() + " " + book.getExtraDetails() + " " + book.getOtherProperty();
        }
    }

    @Introspected
    static class Book {

        private final String title;
        private final String extraDetails;
        private final String otherProperty;

        Book(
            @JsonProperty("title") String title,
            @JsonProperty("extra_details") @Nullable String extraDetails,
            @JsonProperty("other_property") @Nullable String otherProperty
        ) {
            this.title = title;
            this.extraDetails = extraDetails;
            this.otherProperty = otherProperty;
        }

        String getTitle() {
            return title;
        }

        String getExtraDetails() {
            return extraDetails;
        }

        String getOtherProperty() {
            return otherProperty;
        }
    }
}
