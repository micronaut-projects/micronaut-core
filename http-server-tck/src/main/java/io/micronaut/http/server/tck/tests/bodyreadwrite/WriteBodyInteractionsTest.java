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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.Headers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class WriteBodyInteractionsTest {
    public static final String SPEC_NAME = "WriteBodyInteractionsTest";

    @Test
    void stringHeader() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/stringHEADER", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("abc123")
                .build()));
    }

    @Test
    void stringHttpException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/stringHTTP_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("A http exception")
                .build()));
    }

    @Test
    void stringIOException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/stringIO_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .body("IO EXCEPTION")
                .build()));
    }

    @Test
    void bodyHeader() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/byteArrayHEADER", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("abc123")
                .build()));
    }

    @Test
    void bodyHttpException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/byteArrayHTTP_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("A http exception")
                .build()));
    }

    @Test
    void bodyIOException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/byteArrayIO_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .body("IO EXCEPTION")
                .build()));
    }

    @Test
    void inputStreamHeader() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/inputStreamHEADER", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("abc123")
                .build()));
    }

    @Test
    void inputStreamHttpException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/inputStreamHTTP_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.I_AM_A_TEAPOT)
                .body("A http exception")
                .build()));
    }

    @Test
    void inputStreamIOException() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/myController/inputStreamIO_EXCEPTION", "").header("foobar", "abc123"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.ACCEPTED)
                .body("IO EXCEPTION")
                .build()));
    }

    @Controller("/myController")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class MyController {

        @Post("/stringHEADER")
        @Produces(MediaType.TEXT_PLAIN)
        String stringHeader(@Command("HEADER") @Body String foobar) {
            return foobar;
        }

        @Post("/stringHTTP_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        String stringHttpException(@Command("HTTP_EXCEPTION") @Body String foobar) {
            return foobar;
        }

        @Post("/stringIO_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        String stringIO(@Command("IO_EXCEPTION") @Body String foobar) {
            return foobar;
        }

        @Post("/byteArrayHEADER")
        @Produces(MediaType.TEXT_PLAIN)
        byte[] byteArrayHeader(@Command("HEADER") @Body byte[] foobar) {
            return foobar;
        }

        @Post("/byteArrayHTTP_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        byte[] byteArrayHttpException(@Command("HTTP_EXCEPTION") @Body byte[] foobar) {
            return foobar;
        }

        @Post("/byteArrayIO_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        byte[] byteArrayIO(@Command("IO_EXCEPTION") @Body byte[] foobar) {
            return foobar;
        }

        @Post("/inputStreamHEADER")
        @Produces(MediaType.TEXT_PLAIN)
        InputStream inputStreamHeader(@Command("HEADER") @Body InputStream foobar) {
            return foobar;
        }

        @Post("/inputStreamHTTP_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        InputStream inputStreamHttpException(@Command("HTTP_EXCEPTION") @Body InputStream foobar) {
            return foobar;
        }

        @Post("/inputStreamIO_EXCEPTION")
        @Produces(MediaType.TEXT_PLAIN)
        InputStream inputStreamIO(@Command("IO_EXCEPTION") @Body InputStream foobar) {
            return foobar;
        }

        @Error
        HttpResponse<String> onError(ConversionErrorException throwable) {
            return HttpResponse.accepted().body(throwable.getConversionError().getCause().getMessage());
        }
    }

    private static String getCommandValue(Headers httpHeaders, AnnotationValue<Command> annotation) {
        return switch (annotation.stringValue().orElseThrow()) {
            case "HEADER" -> httpHeaders.get("foobar");
            case "HTTP_EXCEPTION" ->
                throw new HttpStatusException(HttpStatus.I_AM_A_TEAPOT, "A http exception");
            case "IO_EXCEPTION" -> throw new CodecException("IO EXCEPTION");
            default -> throw new AssertionError("Unknown command: " + annotation.stringValue());
        };
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Singleton
    static final class StringBodyCommandBodyReader implements MessageBodyReader<String> {

        @Override
        public boolean isReadable(Argument<String> type, MediaType mediaType) {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return annotation != null;
        }

        @Override
        public String read(Argument<String> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return getCommandValue(httpHeaders, annotation);
        }

    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Singleton
    static final class ByteArrayCommandMessageBodyReader implements MessageBodyReader<byte[]> {

        @Override
        public boolean isReadable(Argument<byte[]> type, MediaType mediaType) {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return annotation != null;
        }

        @Override
        public byte[] read(Argument<byte[]> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return getCommandValue(httpHeaders, annotation).getBytes(StandardCharsets.UTF_8);
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Singleton
    static final class InputStreamCommandMessageBodyReader implements MessageBodyReader<InputStream> {

        @Override
        public boolean isReadable(Argument<InputStream> type, MediaType mediaType) {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return annotation != null;
        }

        @Override
        public InputStream read(Argument<InputStream> type, MediaType mediaType, Headers httpHeaders, InputStream inputStream) throws CodecException {
            AnnotationValue<Command> annotation = type.getAnnotation(Command.class);
            return new ByteArrayInputStream(getCommandValue(httpHeaders, annotation).getBytes(StandardCharsets.UTF_8));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface Command {
        String value();
    }

}
