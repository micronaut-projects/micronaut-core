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
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.tck.TestScenario;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import java.io.IOException;
import java.util.Objects;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public interface BodyTest {

    @Test
    default void testCustomBodyPOJO() throws IOException {
        TestScenario.builder()
            .specName("BodyTest")
            .request(HttpRequest.POST("/response-body/pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()))
            .run();
    }

    @Test
    default void testCustomBodyPOJODefaultToJSON() throws IOException {
        TestScenario.builder()
            .specName("BodyTest")
            .request(HttpRequest.POST("/response-body/pojo", "{\"x\":10,\"y\":20}"))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()));
    }

    @Test
    default void testCustomBodyPOJOWithWholeRequest() throws IOException {
        TestScenario.builder()
            .specName("BodyTest")
            .request(HttpRequest.POST("/response-body/pojo-and-request", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> HttpResponseAssertion.builder()
                .status(HttpStatus.CREATED)
                .body("{\"x\":10,\"y\":20}")
                .build())
            .run();
    }

    @Test
    default void testCustomBodyPOJOReactiveTypes() throws IOException {
        TestScenario.builder()
            .specName("BodyTest")
            .request(HttpRequest.POST("/response-body/pojo-reactive", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON))
            .assertion((server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body("{\"x\":10,\"y\":20}")
                    .build()))
            .run();
    }

    @Controller("/response-body")
    @Requires(property = "spec.name", value = "BodyTest")
    class BodyController {

        @Post(uri = "/pojo")
        @Status(HttpStatus.CREATED)
        Point post(@Body Point data) {
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

        @Post(uri = "/bytes", consumes = MediaType.TEXT_PLAIN)
        @Status(HttpStatus.CREATED)
        String postBytes(@Body byte[] bytes) {
            return new String(bytes);
        }
    }

    class Point {
        private Integer x;
        private Integer y;

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
