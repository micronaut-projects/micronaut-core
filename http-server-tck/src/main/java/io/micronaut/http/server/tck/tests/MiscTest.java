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
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;

import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;


@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface MiscTest {
    /**
     *
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/868">micronaut-aws #868</a>
     */
    @Test
    default void testSelectedRouteReflectsAcceptHeader() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.GET("/bar/ok").header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"ok\"}")
                .build()))
            .run();

        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.GET("/bar/ok").header(HttpHeaders.ACCEPT, MediaType.TEXT_HTML))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body("<div>ok</div>")
                    .build()))
            .run();
    }

    @Test
    default void testBehaviourOf404() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.GET("/does-not-exist").header("Accept", MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build()))
            .run();
    }

    @Test
    default void postFormUrlEncodedBodyBindingToPojoWorks() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form", "message=World")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Test
    default void postFormUrlEncodedBodyBindingToPojoWorksIfYouDontSpecifyBodyAnnotation() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/without-body-annotation", "message=World")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Test
    default void formUrlEncodedWithBodyAnnotationAndANestedAttribute() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/nested-attribute", "message=World")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    /**
     *
     * @see <a href="https://github.com/micronaut-projects/micronaut-aws/issues/1410">micronaut-aws #1410</a>
     */
    @Test
    default void applicationJsonWithBodyAnnotationAndANestedAttribute() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/json-nested-attribute", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Test
    default void applicationJsonWithoutBodyAnnotation() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/json-without-body-annotation", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Test
    default void applicationJsonWithBodyAnnotationAndANestedAttributeAndMapReturnRenderedAsJSON() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/json-nested-attribute-with-map-return", "{\"message\":\"World\"}")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"message\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Test
    default void applicationJsonWithBodyAnnotationAndObjectReturnRenderedAsJson() throws IOException {
        TestScenario.builder()
            .specName("MiscTest")
            .request(HttpRequest.POST("/form/json-with-body-annotation-and-with-object-return", "{\"message\":\"World\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"greeting\":\"Hello World\"}")
                .build()))
            .run();
    }

    @Controller
    @Requires(property = "spec.name", value = "MiscTest")
    class SimpleController {
        @Get(uri = "/foo")
        HttpResponse<String> getParamValue(HttpRequest request) {
            return HttpResponse.ok()
                .body(request.getParameters().get("param"))
                .header("foo", "bar");
        }
    }

    @Controller("/bar")
    @Requires(property = "spec.name", value = "MiscTest")
    class ProduceController {
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
    class MessageCreate {

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
    class MyResponse {

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
    @Requires(property = "spec.name", value = "MiscTest")
    class FormController {

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
