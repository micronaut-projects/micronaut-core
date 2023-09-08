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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.micronaut.http.tck.TestScenario.asserts;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HeadersTest {
    public static final String SPEC_NAME = "HeadersTest";

    /**
     * Message header field names are case-insensitive.
     *
     * @see <a href="https://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2">HTTP/1.1 Message Headers</a>
     */
    @Test
    void headersAreCaseInsensitiveAsPerMessageHeadersSpecification() throws IOException {
        // standard header name with mixed case
        asserts(SPEC_NAME,
            HttpRequest.GET("/foo/ok").header("aCcEpT", MediaType.APPLICATION_JSON),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"ok\"}")
                .build()));
        // custom header name with mixed case
        asserts(SPEC_NAME,
            HttpRequest.GET("/foo/bar").header("fOO",  "ok"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("{\"status\":\"okok\"}")
                .build()));
    }

    /**
     * Multiple Headers are properly received as list and not as single header
     */
    @Test
    void multipleHeadersAreReceivedAsList() throws IOException {
        asserts(SPEC_NAME,
            HttpRequest.GET("/foo/receive-multiple-headers")
                    .header(HttpHeaders.ETAG, "A")
                    .header(HttpHeaders.ETAG, "B"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("2")
                .build()));
    }

    @Controller("/foo")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ProduceController {
        @Get(value = "/ok", produces = MediaType.APPLICATION_JSON)
        String getOkAsJson() {
            return "{\"status\":\"ok\"}";
        }

        @Get(value = "/bar", produces = MediaType.APPLICATION_JSON)
        String getFooAsJson(@Header("Foo") String foo, @Header("fOo") String foo2) {
            return "{\"status\":\"" + foo + foo2 + "\"}";
        }

        @Get(value = "/receive-multiple-headers", processes = MediaType.TEXT_PLAIN)
        String receiveMultipleHeaders(HttpRequest<?> request) {
            return String.valueOf(request.getHeaders().getAll(HttpHeaders.ETAG).size());
        }
    }
}
