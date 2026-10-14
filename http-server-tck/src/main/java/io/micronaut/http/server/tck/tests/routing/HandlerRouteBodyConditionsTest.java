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
package io.micronaut.http.server.tck.tests.routing;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.ReflectiveAccess;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.web.router.builder.RouteCondition.body;
import static io.micronaut.web.router.builder.RouteCondition.bodyBytes;
import static io.micronaut.web.router.builder.RouteCondition.bodyText;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The body conditions of handler routes, {@code RouteCondition.body(...)}: routes on the same
 * path chosen by the body of the request, which the chosen route still reads. A request whose
 * body meets no condition falls through, or is not found, and releases the body it read.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteBodyConditionsTest {
    public static final String SPEC_NAME = "HandlerRouteBodyConditionsTest";
    private static final AtomicInteger EVALUATED = new AtomicInteger();
    /**
     * Bytes in a charset: a type the clients send as they are, with the charset of the text.
     */
    private static final MediaType LATIN_1_TEXT = new MediaType("application/x-latin-text", Map.of(MediaType.CHARSET_PARAMETER, StandardCharsets.ISO_8859_1.name()));

    @Test
    void twoRoutesOnTheSamePathChosenByAFieldOfTheJsonBody() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/orders", Map.of("kind", "express", "id", 1)), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("express 1")
                .build());
            // falls through to the other route, which reads the same body
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/orders", Map.of("kind", "standard", "id", 2)), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("standard 2")
                .build());
        }
    }

    @Test
    void twoRoutesOfTheSameRankThatTheBodyBothMatchesAreAmbiguous() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/tie", "apple").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("apples")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/tie", "banana").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("bananas")
                .build());
            // the same URI template, media types and order: a duplicate route, as without body conditions
            AssertionUtils.assertThrows(server, HttpRequest.POST("/bc/tie", "apple banana").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.BAD_REQUEST)
                .build());
            // an order breaks the tie: the fallback pattern
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/tie-ordered", "apple banana").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("apples")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/tie-ordered", "cherry").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback")
                .build());
        }
    }

    @Test
    void textAndBytesConditions() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/raw", "hello world").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("text")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/raw", new byte[]{1, 2, 3}).contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("bytes")
                .build());
            // the text is decoded with the charset of the request
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/charset", "héllo".getBytes(StandardCharsets.ISO_8859_1))
                .contentType(LATIN_1_TEXT), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("latin-1")
                .build());
        }
    }

    @Test
    void aBodyThatMeetsNoConditionIsNotFoundAndReleased() throws IOException {
        try (ServerUnderTest server = server()) {
            // the TCK leak check of the class fails if the read of the conditions leaks a buffer
            for (int i = 0; i < 20; i++) {
                AssertionUtils.assertThrows(server, HttpRequest.POST("/bc/raw", "other " + "x".repeat(i * 100)).contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                    .status(HttpStatus.NOT_FOUND)
                    .build());
            }
        }
    }

    @Test
    void aBodyLargerThanTheLimitGoesToTheNextRouteOrIsNotFound() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/limited", "12345678").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("small 12345678")
                .build());
            // the next route reads the whole body
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/limited", "123456789").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback 123456789")
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.POST("/bc/limited-only", "123456789").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/limited-only", "1234").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("limited")
                .build());
        }
    }

    @Test
    void aBodyThatDoesNotDecodeDoesNotMeetTheCondition() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/decode", "{\"kind\":\"express\",\"id\":3}").contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("decoded 3")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/decode", "{not json").contentType(MediaType.APPLICATION_JSON_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback")
                .build());
        }
    }

    @Test
    void theChosenRouteReadsTheBodyAsAsyncRequestBodyAndAsAForm() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/async-body", "async text").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("read async text")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/form", "name=Fred&kind=vip").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("vip Fred")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/form", "name=Wilma&kind=regular").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("regular Wilma")
                .build());
        }
    }

    @Test
    void theOtherConditionsOfTheRouteAreMetBeforeTheBodyIsRead() throws IOException {
        try (ServerUnderTest server = server()) {
            int evaluated = EVALUATED.get();
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/headed", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("plain")
                .build());
            assertEquals(evaluated, EVALUATED.get(), "the body condition of a route whose header condition fails is not evaluated");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/headed", "x").header("X-Express", "1").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("express")
                .build());
            assertEquals(evaluated + 1, EVALUATED.get());
        }
    }

    @Test
    void theConditionSeesTheBodyAPreMatchingFilterSet() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/filtered", "standard").header("X-Express", "yes").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("express express")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/filtered", "standard").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("fallback standard")
                .build());
        }
    }

    @Test
    void theImplicitHeadRouteSeesTheEmptyBodyOfAHeadRequest() throws IOException {
        try (ServerUnderTest server = server()) {
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.GET("/bc/report"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("report")
                .build());
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.HEAD("/bc/report"), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
            AssertionUtils.assertThrows(server, HttpRequest.HEAD("/bc/report-with-body"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void aMethodNotAllowedIsAnsweredWithoutReadingTheBody() throws IOException {
        try (ServerUnderTest server = server()) {
            int evaluated = EVALUATED.get();
            AssertionUtils.assertThrows(server, HttpRequest.PUT("/bc/counted", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
            assertEquals(evaluated, EVALUATED.get(), "the body condition is not evaluated");
            AssertionUtils.assertDoesNotThrow(server, HttpRequest.POST("/bc/counted", "x").contentType(MediaType.TEXT_PLAIN_TYPE), HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("counted")
                .build());
            assertEquals(evaluated + 1, EVALUATED.get());
        }
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Introspected
    @ReflectiveAccess
    record Order(String kind, int id) {
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyConditionRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.POST("/bc/orders")
                .where(body(Order.class, order -> "express".equals(order.kind())))
                .order(-1)
                .body(Order.class)
                .handle((request, pathVariables, order) -> text("express " + order.id()));
            routes.POST("/bc/orders").body(Order.class).handle((request, pathVariables, order) -> text("standard " + order.id()));

            routes.POST("/bc/tie").where(bodyText(text -> text.contains("apple"))).consumesAll()
                .handle((request, pathVariables) -> text("apples"));
            routes.POST("/bc/tie").where(bodyText(text -> text.contains("banana"))).consumesAll()
                .handle((request, pathVariables) -> text("bananas"));
            routes.POST("/bc/tie-ordered").where(bodyText(text -> text.contains("apple"))).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("apples"));
            routes.POST("/bc/tie-ordered").consumesAll()
                .handle((request, pathVariables) -> text("fallback"));

            routes.POST("/bc/raw").where(bodyText(text -> text.startsWith("hello"))).consumesAll()
                .handle((request, pathVariables) -> text("text"));
            routes.POST("/bc/raw").where(bodyBytes(bytes -> bytes.length > 0 && bytes[0] == 1)).consumesAll()
                .handle((request, pathVariables) -> text("bytes"));
            routes.POST("/bc/charset").where(bodyText("héllo"::equals)).consumesAll()
                .handle((request, pathVariables) -> text("latin-1"));

            routes.POST("/bc/limited").where(bodyText(text -> true).maxBytes(8)).order(-1).consumesAll()
                .body(String.class).handle((request, pathVariables, body) -> text("small " + body));
            routes.POST("/bc/limited").consumesAll()
                .body(String.class).handle((request, pathVariables, body) -> text("fallback " + body));
            routes.POST("/bc/limited-only").where(bodyText(text -> true).maxBytes(8)).consumesAll()
                .handle((request, pathVariables) -> text("limited"));

            routes.POST("/bc/decode").where(body(Order.class, order -> true)).order(-1)
                .body(Order.class).handle((request, pathVariables, order) -> text("decoded " + order.id()));
            routes.POST("/bc/decode").order(1)
                .handle((request, pathVariables) -> text("fallback"));

            routes.POST("/bc/async-body").where(bodyText(text -> text.startsWith("async"))).consumesAll()
                .body().handleAsync((request, pathVariables, body) -> body.text().thenApply(text -> text("read " + text)));

            routes.POST("/bc/form").where(bodyText(text -> text.contains("kind=vip"))).order(-1)
                .form().handle((request, pathVariables, form) -> text("vip " + form.getString("name")));
            routes.POST("/bc/form")
                .form().handle((request, pathVariables, form) -> text("regular " + form.getString("name")));

            routes.POST("/bc/headed").where(header("X-Express").and(bodyText(text -> EVALUATED.incrementAndGet() > 0))).order(-1).consumesAll()
                .handle((request, pathVariables) -> text("express"));
            routes.POST("/bc/headed").consumesAll()
                .handle((request, pathVariables) -> text("plain"));

            routes.serverFilter("/bc/filtered").preMatching().beforeReplacing(request ->
                request.getHeaders().contains("X-Express") ? request.body("express") : null);
            routes.POST("/bc/filtered").where(bodyText("express"::equals)).order(-1).consumesAll()
                .body(String.class).handle((request, pathVariables, body) -> text("express " + body));
            routes.POST("/bc/filtered").consumesAll()
                .body(String.class).handle((request, pathVariables, body) -> text("fallback " + body));

            routes.GET("/bc/report").where(bodyText(String::isEmpty))
                .handle((request, pathVariables) -> text("report"));
            routes.GET("/bc/report-with-body").where(bodyText(text -> !text.isEmpty()))
                .handle((request, pathVariables) -> text("report"));

            routes.POST("/bc/counted").where(bodyText(text -> EVALUATED.incrementAndGet() > 0)).consumesAll()
                .handle((request, pathVariables) -> text("counted"));
            routes.GET("/bc/counted", (request, pathVariables) -> text("get"));
        }
    }
}
