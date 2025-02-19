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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.BodyAssertion;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class NoBodyResponseTest {
    public static final String SPEC_NAME = "MissingBodyTest";

    private final Map<String, Object> disableNotFoundOnMissingBodyConf = Map.of("micronaut.server.not-found-on-missing-body", "false");

    @Test
    void getPojoStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-status"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoStatusDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-status"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojo() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoStatusFuture() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-status-future"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoStatusFutureDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-status-future"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoFuture() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-future"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoFutureDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-future"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoFutureResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-future-response"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoFutureResponseDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-future-response"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoStatusPublisher() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-status-publisher"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoStatusPublisherDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-status-publisher"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoPublisher() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-publisher"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoPublisherDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-publisher"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void getPojoPublisherResponse() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/response-no-body/get-pojo-publisher-response"),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void getPojoPublisherResponseDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.GET("/response-no-body/get-pojo-publisher-response"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void postReturnsPojoStatus() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-no-body/post-returns-pojo-status", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void postReturnsPojoStatusDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.POST("/response-no-body/post-returns-pojo-status", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.CREATED)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void postReturnsPojo() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-no-body/post-returns-pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void postReturnsPojoDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.POST("/response-no-body/post-returns-pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.NO_CONTENT)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Test
    void postReturnsResponsePojo() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.POST("/response-no-body/post-returns-response-pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertThrowsStatus(HttpStatus.NOT_FOUND));
    }

    @Test
    void postReturnsResponsePojoDisabled() throws IOException {
        asserts(SPEC_NAME,
            disableNotFoundOnMissingBodyConf,
            HttpRequest.POST("/response-no-body/post-returns-response-pojo", "{\"x\":10,\"y\":20}")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request,
                HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .body(BodyAssertion.IS_MISSING)
                    .build()));
    }

    @Controller("/response-no-body")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class BodyController {

        @Get("/get-pojo-status")
        @Status(HttpStatus.CREATED)
        Point getPojoStatus() {
            return null;
        }

        @Get("/get-pojo")
        Point getPojo() {
            return null;
        }

        @Get("/get-pojo-status-future")
        @Status(HttpStatus.CREATED)
        CompletableFuture<Point> getPojoStatusFuture() {
            return CompletableFuture.completedFuture(null);
        }

        @Get("/get-pojo-future")
        CompletableFuture<Point> getPojoFuture() {
            return CompletableFuture.completedFuture(null);
        }

        @Get("/get-pojo-future-response")
        CompletableFuture<HttpResponse<Point>> getPojoFutureResponse() {
            return CompletableFuture.completedFuture(HttpResponse.ok());
        }

        @SingleResult
        @Get("/get-pojo-status-publisher")
        @Status(HttpStatus.CREATED)
        Publisher<Point> getPojoStatusPublisher() {
            return Mono.empty();
        }

        @SingleResult
        @Get("/get-pojo-publisher")
        Publisher<Point> getPojoFuturePublisher() {
            return Mono.empty();
        }

        @Get("/get-pojo-publisher-response")
        Publisher<HttpResponse<Point>> getPojoFuturePublisherResponse() {
            return Mono.just(HttpResponse.ok());
        }

        @Post(uri = "/post-returns-pojo-status")
        @Status(HttpStatus.CREATED)
        Point postBodyReturnsPojo(@Body Point data) {
            return null;
        }

        @Post(uri = "/post-returns-pojo")
        Point postNoBodyReturnsPojo() {
            return null;
        }

        @Post(uri = "/post-returns-response-pojo")
        HttpResponse<Point> postBodyReturnsPojoResponse(@Body Point data) {
            return HttpResponse.ok();
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
