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
package io.micronaut.http.server.tck.tests.codec;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.core.type.MutableHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyHandler;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Media types listed in {@code micronaut.codec.json.additional-types} are read and written by the
 * JSON handler, unless a handler is registered for the exact media type.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class JsonCodecAdditionalTypeHandlerTest {
    public static final String SPEC_NAME = "JsonCodecAdditionalTypeHandlerTest";
    public static final String ACME_JSON = "application/vnd.acme+json";
    public static final String HAL_FORMS_JSON = "application/prs.hal-forms+json";
    public static final String CUSTOM_JSON = "application/vnd.custom+json";
    private static final Map<String, Object> CONFIGURATION = Map.of(
        "micronaut.codec.json.additional-types", List.of(ACME_JSON, HAL_FORMS_JSON, CUSTOM_JSON)
    );

    @Test
    void writesBeanForAdditionalJsonType() throws IOException {
        assertWrites("/json-additional-handler/acme", ACME_JSON);
        assertWrites("/json-additional-handler/hal-forms", HAL_FORMS_JSON);
    }

    @Test
    void readsBeanForAdditionalJsonType() throws IOException {
        asserts(SPEC_NAME, CONFIGURATION,
            HttpRequest.POST("/json-additional-handler/acme", "{\"name\":\"posted\",\"count\":7}").contentType(ACME_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("posted:7")
                .build()));
    }

    @Test
    void exactHandlerForAdditionalJsonTypeWins() throws IOException {
        asserts(SPEC_NAME, CONFIGURATION,
            HttpRequest.GET("/json-additional-handler/custom").header(HttpHeaders.ACCEPT, CUSTOM_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("{\"custom-writer\":\"widget\"}").equals())
                .assertResponse(response -> assertTrue(response.header(HttpHeaders.CONTENT_TYPE).contains(CUSTOM_JSON)))
                .build()));
        asserts(SPEC_NAME, CONFIGURATION,
            HttpRequest.POST("/json-additional-handler/custom", "{\"name\":\"posted\",\"count\":7}").contentType(CUSTOM_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("custom-reader:0")
                .build()));
    }

    private static void assertWrites(String uri, String mediaType) throws IOException {
        asserts(SPEC_NAME, CONFIGURATION,
            HttpRequest.GET(uri).header(HttpHeaders.ACCEPT, mediaType),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body(BodyAssertion.builder().body("{\"name\":\"widget\",\"count\":3}").equals())
                .assertResponse(response -> assertTrue(response.header(HttpHeaders.CONTENT_TYPE).contains(mediaType)))
                .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/json-additional-handler")
    static class AdditionalTypeController {

        @Get("/acme")
        @Produces(ACME_JSON)
        Widget acme() {
            return new Widget("widget", 3);
        }

        @Get("/hal-forms")
        @Produces(HAL_FORMS_JSON)
        Widget halForms() {
            return new Widget("widget", 3);
        }

        @Post("/acme")
        @Consumes(ACME_JSON)
        @Produces(MediaType.TEXT_PLAIN)
        String postAcme(@Body Widget widget) {
            return widget.name() + ":" + widget.count();
        }

        @Get("/custom")
        @Produces(CUSTOM_JSON)
        Widget custom() {
            return new Widget("widget", 3);
        }

        @Post("/custom")
        @Consumes(CUSTOM_JSON)
        @Produces(MediaType.TEXT_PLAIN)
        String postCustom(@Body Widget widget) {
            return widget.name() + ":" + widget.count();
        }
    }

    @Introspected
    record Widget(String name, int count) {
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Singleton
    @Produces(CUSTOM_JSON)
    @Consumes(CUSTOM_JSON)
    static class CustomWidgetHandler implements MessageBodyHandler<Widget> {

        @Override
        public @Nullable Widget read(Argument<Widget> type, @Nullable MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            try {
                inputStream.readAllBytes();
            } catch (IOException e) {
                throw new CodecException("Failed to read the body", e);
            }
            return new Widget("custom-reader", 0);
        }

        @Override
        public void writeTo(Argument<Widget> type, MediaType mediaType, Widget object, MutableHeaders outgoingHeaders, OutputStream outputStream) throws CodecException {
            outgoingHeaders.set(HttpHeaders.CONTENT_TYPE, mediaType);
            try {
                outputStream.write(("{\"custom-writer\":\"" + object.name() + "\"}").getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new CodecException("Failed to write the body", e);
            }
        }
    }
}
