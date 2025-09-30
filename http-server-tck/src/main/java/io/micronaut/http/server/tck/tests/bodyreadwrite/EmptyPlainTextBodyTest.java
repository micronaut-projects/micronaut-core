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
package io.micronaut.http.server.tck.tests.bodyreadwrite;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class EmptyPlainTextBodyTest {
    public static final String SPEC_NAME = "EmptyPlainTextBodyTest";

    @Test
    void stringBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/string", "FooBar").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void bytesBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/bytes", "FooBar").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void ioBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/io", "FooBar").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void stringEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/string", "").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void bytesEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/bytes", "").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void ioEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/io", "").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void ioNullableEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/ioNullable", "").accept(MediaType.TEXT_PLAIN).contentType(MediaType.TEXT_PLAIN),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.TEXT_PLAIN)
    @Controller("/myController")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyController {

        @Post("/string")
        String string(@Body String foobar) {
            return foobar;
        }

        @Post("/bytes")
        byte[] bytes(@Body byte[] foobar) {
            return foobar;
        }

        @Post("/io")
        InputStream inputStream(@Body InputStream is) {
            return is;
        }

        @Post("/ioNullable")
        InputStream inputNullableStream(@Body @Nullable InputStream is) {
            if (is == null) {
                return new ByteArrayInputStream(new byte[0]);
            }
            return is;
        }

    }


}
