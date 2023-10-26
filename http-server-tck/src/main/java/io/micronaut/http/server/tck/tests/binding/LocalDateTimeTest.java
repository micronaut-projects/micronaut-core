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
package io.micronaut.http.server.tck.tests.binding;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.BodyAssertion;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDateTime;

import static io.micronaut.http.server.tck.TestScenario.asserts;

@SuppressWarnings({
        "java:S5960", // We're allowed assertions, as these are used in tests only
        "checkstyle:MissingJavadocType",
        "checkstyle:DesignForExtension"
})
public class LocalDateTimeTest {
    public static final String SPEC_NAME = "ControllerConstraintHandlerTest";

    @Test
    void getQueryValueWithLocalDateTimeWithoutSeconds() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.GET("/localdatetime/get?eventDate=2023-10-28T10%3A00"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("{\"eventDate\":\"2023-10-28T10:00\"}").equals())
                        .build()));
    }

    @Test
    void getQueryValueWithLocalDateTimeWithSeconds() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.GET("/localdatetime/get?eventDate=2023-10-28T10%3A20%3A23"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("{\"eventDate\":\"2023-10-28T10:20:23\"}").equals())
                        .build()));
    }

    @Test
    void formUrlEncodedPostWithLocalDateTimeWithoutSeconds() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.POST("/localdatetime/save", "eventDate=2023-10-28T10%3A00").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("{\"eventDate\":\"2023-10-28T10:00\"}").equals())
                        .build()));
    }

    @Test
    void formUrlEncodedPostWithLocalDateTimeWithSeconds() throws IOException {
        asserts(SPEC_NAME,
                HttpRequest.POST("/localdatetime/save/seconds", "eventDate=2023-10-28T10%3A20%3A23").contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .body(BodyAssertion.builder().body("{\"eventDate\":\"2023-10-28T10:20:23\"}").equals())
                        .build()));
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/localdatetime")
    static class SaveController {

        @Get("/get{?eventDate")
        String get(@QueryValue LocalDateTime eventDate) {
            return "{\"eventDate\":\"" + eventDate.toString() + "\"}";
        }

        @Get("/get/seconds{?eventDate")
        String getSeconds(@QueryValue LocalDateTime eventDate) {
            return "{\"eventDate\":\"" + eventDate.toString() + "\"}";
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/save")
        String save(@Body Event event) {
            return "{\"eventDate\":\"" + event.getEventDate().toString() + "\"}";
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/save/seconds")
        String saveSeconds(@Body Event event) {
            return "{\"eventDate\":\"" + event.getEventDate().toString() + "\"}";
        }
    }

    @Introspected
    public static class Event {
        private final LocalDateTime eventDate;

        public Event(LocalDateTime eventDate) {
            this.eventDate = eventDate;
        }

        public LocalDateTime getEventDate() {
            return eventDate;
        }
    }
}

