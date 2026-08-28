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
package io.micronaut.http.client.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RedirectHeaderCopyTest {
    private static final String SPEC_NAME = "RedirectHeaderCopyTest";
    private static final String BLOCKING_CLIENT_PROPERTY = "use.blocking.client";

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    void copiedHeadersForDirectRequest(boolean blocking) throws IOException {
        assertHeaders(blocking,
            "/redirect/echo-headers",
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer direct",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-direct",
                HttpHeaders.COOKIE, "one=direct",
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                "X-UnknownHeader", "direct"
            ),
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer direct",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-direct",
                HttpHeaders.COOKIE, "one=direct",
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                "X-UnknownHeader", "direct"
            ));
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    void copiedHeadersForSameOriginRedirect(boolean blocking) throws IOException {
        assertHeaders(blocking,
            "/same-origin-redirect/redirect-to-echo",
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer same-origin",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-same-origin",
                HttpHeaders.COOKIE, "one=same-origin",
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                "X-UnknownHeader", "same-origin"
            ),
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer same-origin",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-same-origin",
                HttpHeaders.COOKIE, "one=same-origin",
                "X-UnknownHeader", "same-origin"
            ));
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("java:S3655")
    void copiedHeadersForCrossOriginRedirect(boolean blocking) throws IOException {
        try (ServerUnderTest otherServer = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Collections.singletonMap("redirect.server", "true"))) {
            assertHeaders(blocking,
                "/cross-origin-redirect/redirect-to-echo?redirect-server-port=" + otherServer.getPort().orElseThrow(),
                Map.of(
                    HttpHeaders.AUTHORIZATION, "Bearer cross-origin",
                    HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-cross-origin",
                    HttpHeaders.COOKIE, "one=cross-origin",
                    "X-UnknownHeader", "cross-origin"
                ),
                Map.of(
                    "X-UnknownHeader", "cross-origin"
                ));
        }
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("java:S3655")
    void headersAreRetainedForSameOrigin307Redirect(boolean blocking) throws IOException {
        assertPostHeaders(blocking,
            "/same-origin-redirect/redirect307-to-echo",
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer same-origin-307",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-same-origin-307",
                HttpHeaders.COOKIE, "one=same-origin-307",
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                "X-UnknownHeader", "same-origin-307"
            ),
            Map.of(
                HttpHeaders.AUTHORIZATION, "Bearer same-origin-307",
                HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-same-origin-307",
                HttpHeaders.COOKIE, "one=same-origin-307",
                HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                "content-length", "4",
                "X-UnknownHeader", "same-origin-307",
                "X-Method", "POST"
            ));
    }

    @ParameterizedTest(name = "blocking={0}")
    @ValueSource(booleans = {true, false})
    @SuppressWarnings("java:S3655")
    void headersAreRetainedForCrossOrigin307Redirect(boolean blocking) throws IOException {
        try (ServerUnderTest otherServer = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Collections.singletonMap("redirect.server", "true"))) {
            assertPostHeaders(blocking,
                "/cross-origin-redirect/redirect307-to-echo?redirect-server-port=" + otherServer.getPort().orElseThrow(),
                Map.of(
                    HttpHeaders.AUTHORIZATION, "Bearer cross-origin-307",
                    HttpHeaders.PROXY_AUTHORIZATION, "Basic proxy-cross-origin-307",
                    HttpHeaders.COOKIE, "one=cross-origin-307",
                    HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                    "X-UnknownHeader", "cross-origin-307"
                ),
                Map.of(
                    HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_TYPE.toString(),
                    "content-length", "4",
                    "X-UnknownHeader", "cross-origin-307",
                    "X-Method", "POST"
                ));
        }
    }

    private void assertHeaders(boolean blocking, String uri, Map<String, String> requestHeaders, Map<String, String> expectedHeaders) throws IOException {
        assertHeaders(blocking, uri, requestHeaders, expectedHeaders, null);
    }

    private void assertHeaders(boolean blocking, String uri, Map<String, String> requestHeaders, Map<String, String> expectedHeaders, Integer redirectPort) throws IOException {
        var request = HttpRequest.GET(uri);
        requestHeaders.forEach(request::header);
        Map<String, Object> properties = redirectPort == null ? Map.of(BLOCKING_CLIENT_PROPERTY, blocking) : Map.of(
            BLOCKING_CLIENT_PROPERTY, blocking,
            "redirect.server.port", Integer.toString(redirectPort)
        );
        asserts(SPEC_NAME,
            properties,
            request,
            (server, requestUnderTest) -> {
                HttpResponse<Map> response = server.exchange(requestUnderTest, Map.class);
                assertEquals(HttpStatus.OK, response.getStatus());
                assertEquals(normalizeHeaders(expectedHeaders), normalizeHeaders(response.body()));
            }
        );
    }

    private void assertPostHeaders(boolean blocking, String uri, Map<String, String> requestHeaders, Map<String, String> expectedHeaders) throws IOException {
        var request = HttpRequest.POST(uri, "body");
        requestHeaders.forEach(request::header);
        asserts(SPEC_NAME,
            Map.of(BLOCKING_CLIENT_PROPERTY, blocking),
            request,
            (server, requestUnderTest) -> {
                HttpResponse<Map> response = server.exchange(requestUnderTest, Map.class);
                assertEquals(HttpStatus.OK, response.getStatus());
                assertEquals(normalizeHeaders(expectedHeaders), normalizeHeaders(response.body()));
            }
        );
    }

    private Map<String, String> normalizeHeaders(Map<?, ?> actualHeaders) {
        TreeMap<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        actualHeaders.forEach((key, value) -> normalized.put(String.valueOf(key), String.valueOf(value)));
        return normalized;
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class HeaderEchoController {

        @Get("/echo-headers")
        Map<String, String> echoHeaders(HttpRequest<?> request) {
            Map<String, String> headers = new LinkedHashMap<>();
            request.getHeaders().forEach((name, values) -> {
                if (!HttpHeaders.CONNECTION.equalsIgnoreCase(name)
                    && !HttpHeaders.HOST.equalsIgnoreCase(name)
                    && !"Upgrade".equalsIgnoreCase(name)
                    && !"HTTP2-Settings".equalsIgnoreCase(name)
                    && !HttpHeaders.USER_AGENT.equalsIgnoreCase(name)) {
                    headers.put(name, values.get(0));
                }
            });
            return headers;
        }

        @Post("/echo-post-headers")
        @Consumes(MediaType.ALL)
        Map<String, String> echoPostHeaders(HttpRequest<?> request) {
            Map<String, String> headers = echoHeaders(request);
            headers.put("X-Method", request.getMethodName());
            return headers;
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/same-origin-redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class SameOriginRedirectController {

        @Get("/redirect-to-echo")
        HttpResponse<?> redirectToEcho() {
            return HttpResponse.redirect(URI.create("/redirect/echo-headers"));
        }

        @Post("/redirect307-to-echo")
        @Consumes(MediaType.ALL)
        HttpResponse<?> redirect307ToEcho() {
            return HttpResponse.status(HttpStatus.TEMPORARY_REDIRECT).headers(headers -> headers.location(URI.create("/redirect/echo-post-headers")));
        }
    }

    @Requires(property = "spec.name", value = SPEC_NAME)
    @Controller("/cross-origin-redirect")
    @SuppressWarnings("checkstyle:MissingJavadocType")
    static class CrossOriginRedirectController {

        @Get("/redirect-to-echo")
        HttpResponse<?> redirectToEcho(@QueryValue("redirect-server-port") String redirectServerPort) {
            return HttpResponse.redirect(URI.create("http://localhost:" + redirectServerPort + "/redirect/echo-headers"));
        }

        @Post("/redirect307-to-echo")
        @Consumes(MediaType.ALL)
        HttpResponse<?> redirect307ToEcho(@QueryValue("redirect-server-port") String redirectServerPort) {
            return HttpResponse.status(HttpStatus.TEMPORARY_REDIRECT)
                .headers(headers -> headers.location(URI.create("http://localhost:" + redirectServerPort + "/redirect/echo-post-headers")));
        }
    }
}
