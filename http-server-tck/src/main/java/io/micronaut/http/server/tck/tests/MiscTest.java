/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class MiscTest {
    public static final String SPEC_NAME = "MiscTest";

    /**
     *
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/868">micronaut-aws #868</a>
     */
    @Test
    void testSelectedRouteReflectsAcceptHeader() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/bar/ok").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"ok\"}")
                .build()));

        asserts(SPEC_NAME,
            HttpRequest.GET("/bar/ok").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("<div>ok</div>")
                    .build()));
    }

    @Test
    void testBehaviourOf404() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/does-not-exist").header("Accept", MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()));
    }

    @Test
    void postFormUrlEncodedBodyBindingToPojoWorks() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form", "message=World").header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    @Test
    @Disabled("not supported anymore")
    void postFormUrlEncodedBodyBindingToPojoWorksIfYouDontSpecifyBodyAnnotation() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/without-body-annotation", "message=World")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    @Test
    void formUrlEncodedWithBodyAnnotationAndANestedAttribute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/nested-attribute", "message=World")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    /**
     *
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/1410">micronaut-aws #1410</a>
     */
    @Test
    void applicationJsonWithBodyAnnotationAndANestedAttribute() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/json-nested-attribute", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    @Test
    @Disabled("not supported anymore")
    void applicationJsonWithoutBodyAnnotation() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/json-without-body-annotation", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    @Test
    void applicationJsonWithBodyAnnotationAndANestedAttributeAndMapReturnRenderedAsJSON() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/json-nested-attribute-with-map-return", "{\"message\":\"World\"}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()));
    }

    @Test
    void applicationJsonWithBodyAnnotationAndObjectReturnRenderedAsJson() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/form/json-with-body-annotation-and-with-object-return", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"greeting\":\"Hello World\"}")
                .build()));
    }

    @Controller
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class SimpleController {
        @Get(uri = "/foo")
        HttpResponse<String> getParamValue(HttpRequest request) {
            return HttpResponse.ok()
                .body(request.getParameters().get("param"))
                .header("foo", "bar");
        }
    }

    @Controller("/bar")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProduceController {
        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        String getOkAsJson() {
            return "{\"status\":\"ok\"}";
        }

        @Get(value = "/ok", produces = MediaType.TEXT_HTML)
        String getOkAsHtml() {
            return "<div>ok</div>";
        }
    }

    @Introspected
    static class MessageCreate {

        @NonNull
        @NotBlank
        private final String message;

        MessageCreate(@NonNull String message) {
            this.message = message;
        }

        @NonNull
        String getMessage() {
            return message;
        }
    }

    @Introspected
    static class MyResponse {

        @NonNull
        @NotBlank
        private final String greeting;

        public MyResponse(@NonNull String greeting) {
            this.greeting = greeting;
        }

        @NonNull
        public String getGreeting() {
            return greeting;
        }
    }

    @Controller("/form")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class FormController {

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/without-body-annotation")
        String withoutBodyAnnotation(MessageCreate messageCreate) {
            return "{\"message\":\"Hello " + messageCreate.getMessage() + "\"}";
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post
        String save(@Body MessageCreate messageCreate) {
            return "{\"message\":\"Hello " + messageCreate.getMessage() + "\"}";
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/nested-attribute")
        String save(@Body("message") String value) {
            return "{\"message\":\"Hello " + value + "\"}";
        }

        @Consumes(MediaType.APPLICATION_JSON)
        @Post("/json-without-body-annotation")
        String jsonWithoutBody(MessageCreate messageCreate) {
            return "{\"message\":\"Hello " + messageCreate.getMessage() + "\"}";
        }

        @Consumes(MediaType.APPLICATION_JSON)
        @Post("/json-nested-attribute")
        String jsonNestedAttribute(@Body("message") String value) {
            return "{\"message\":\"Hello " + value + "\"}";
        }

        @Consumes(MediaType.APPLICATION_JSON)
        @Post("/json-nested-attribute-with-map-return")
        Map<String, String> jsonNestedAttributeWithMapReturn(@Body("message") String value) {
            return Collections.singletonMap("message", "Hello " + value);
        }

        @Consumes(MediaType.APPLICATION_JSON)
        @Post("/json-with-body-annotation-and-with-object-return")
        MyResponse jsonNestedAttributeWithObjectReturn(@Body MessageCreate messageCreate) {
            return new MyResponse("Hello " + messageCreate.getMessage());
        }
    }
}
