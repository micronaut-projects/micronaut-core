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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Objects;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class BodyTest {
    public static final String SPEC_NAME = "BodyTest";

    @Test
    void testCustomBodyPOJO() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()));
    }

    @Test
    void testCustomBodyPOJOAsPart() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/part-pojo", "{\"point\":{\"x\":10,\"y\":20},\"foo\":\"bar\"}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()));
    }

    @Test
    void testCustomBodyPOJODefaultToJSON() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/pojo", "{\"x\":10,\"y\":20}"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()));
    }

    @Test
    void testCustomBodyPOJOWithWholeRequest() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/pojo-and-request", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"x\":10,\"y\":20}")
                .build());
    }

    @Test
    void testCustomBodyPOJOReactiveTypes() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/pojo-reactive", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()));
    }

    @Test
    void testCustomListBodyPOJOReactiveTypes() throws IOException {
        String body = "[{\"x\":10,\"y\":20},{\"x\":30,\"y\":40}]";
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/pojo-flux", body)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.builder().body(body).equals())
                    .build()));
    }

    @Test
    void testRequestBodyJsonNoBodyAnnotation() throws IOException {
        String body = "{\"x\":10,\"y\":20}";
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/args-no-body", body)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.builder().body(body).equals())
                    .build()));
    }

    @Test
    void testRequestBodyFormDataNoBodyAnnotation() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-body/args-no-body-form", "x=10&y=20")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.builder().body("{\"x\":10,\"y\":20}").equals())
                    .build()));
    }

    @Controller("/response-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyController {

        @Post(uri = "/pojo")
        @Status(HttpStatus.CREATED)
        Point post(@Body Point data) {
            return data;
        }

        @Post(uri = "/args-no-body")
        @Status(HttpStatus.CREATED)
        Point postNoBody(Integer x, Integer y) {
            return new Point(x,y);
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/args-no-body-form")
        @Status(HttpStatus.CREATED)
        Point postNoBodyFormData(Integer x, Integer y) {
            return new Point(x,y);
        }

        @Post(uri = "/part-pojo")
        @Status(HttpStatus.CREATED)
        Point postPart(@Body("point") Point data) {
            return data;
        }

        @Post(uri = "/pojo-and-request")
        @Status(HttpStatus.CREATED)
        Point postRequest(HttpRequest<Point> request) {
            return request.getBody().orElse(null);
        }

        @Post(uri = "/pojo-reactive")
        @Status(HttpStatus.CREATED)
        @SingleResult
        Publisher<Point> post(@Body Publisher<Point> data) {
            return data;
        }

        @Post(uri = "/pojo-flux")
        @Status(HttpStatus.CREATED)
        Publisher<Point> postMany(@Body Publisher<Point> data) {
            return data;
        }

        @Post(uri = "/bytes", consumes = MediaType.TEXT_PLAIN)
        @Status(HttpStatus.CREATED)
        String postBytes(@Body byte[] bytes) {
            return new String(bytes);
        }
    }

    @Introspected
    static class Point {
        private Integer x;
        private Integer y;

        public Point(Integer x, Integer y) {
            this.x = x;
            this.y = y;
        }

        public Integer getX() {
            return x;
        }

        public void setX(Integer x) {
            this.x = x;
        }

        public Integer getY() {
            return y;
        }

        public void setY(Integer y) {
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Point point = (Point) o;

            if (!Objects.equals(x, point.x)) {
                return false;
            }
            return Objects.equals(y, point.y);
        }

        @Override
        public int hashCode() {
            int result = x != null ? x.hashCode() : 0;
            result = 31 * result + (y != null ? y.hashCode() : 0);
            return result;
        }
    }

}
