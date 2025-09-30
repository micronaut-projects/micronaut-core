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
import io.micronaut.core.annotation.Introspected;
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
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class EmptyJsonBodyTest {
    public static final String SPEC_NAME = "EmptyJsonBodyTest";

    private Map<String, Object> getConfiguration() {
        return Map.of(
            "micronaut.server.not-found-on-missing-body", "false"
        );
    }

    @Test
    void stringBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/string", "FooBar").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void bytesBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/bytes", "FooBar").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void ioBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/io", "FooBar").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("FooBar")
                .build()));
    }

    @Test
    void beanEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/bean", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NO_CONTENT)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void stringEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/string", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void bytesEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/bytes", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void ioEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/io", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.IS_MISSING)
                .build()));
    }

    @Test
    void stringNullableBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/stringNullable", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("<null>")
                .build()));
    }

    @Test
    void bytesNullableBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/bytesNullable", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("<null>")
                .build()));
    }

    @Test
    void ioNullableEmptyBody() throws IOException {
        asserts(SPEC_NAME,
            getConfiguration(),
            HttpRequest.POST("/myController/ioNullable", "").accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("<null>")
                .build()));
    }

    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Controller("/myController")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyController {

        @Post("/bean")
        MyBean bean(@Body @Nullable MyBean bean) {
            return bean;
        }

        @Post("/string")
        String string(@Body String foobar) {
            return foobar;
        }

        @Post("/stringNullable")
        String stringNullable(@Body @Nullable String foobar) {
            if (foobar == null) {
                return nullBodyValue();
            }
            return foobar;
        }

        @Post("/bytes")
        byte[] bytes(@Body byte[] foobar) {
            return foobar;
        }

        @Post("/bytesNullable")
        byte[] bytesNullable(@Nullable @Body byte[] foobar) {
            if (foobar == null) {
                return nullBodyValue().getBytes(StandardCharsets.UTF_8);
            }
            return foobar;
        }

        @Post("/io")
        InputStream inputStream(@Body InputStream is) {
            return is;
        }

        @Post("/ioNullable")
        InputStream inputNullableStream(@Body @Nullable InputStream is) {
            if (is == null) {
                return new ByteArrayInputStream(nullBodyValue().getBytes(StandardCharsets.UTF_8));
            }
            return is;
        }

        private String nullBodyValue() {
            return "<null>";
        }

    }

    @Introspected
    record MyBean() {
    }

}
