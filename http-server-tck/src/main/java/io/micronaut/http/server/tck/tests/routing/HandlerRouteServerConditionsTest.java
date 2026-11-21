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

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static io.micronaut.web.router.builder.RequestCondition.after;
import static io.micronaut.web.router.builder.RequestCondition.before;
import static io.micronaut.web.router.builder.RequestCondition.between;
import static io.micronaut.web.router.builder.RequestCondition.cookie;
import static io.micronaut.web.router.builder.RequestCondition.host;
import static io.micronaut.web.router.builder.RequestCondition.peerAddress;
import static io.micronaut.web.router.builder.RequestCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.regex;

/**
 * The host, cookie, client address and time conditions of {@link RequestCondition} on handler
 * routes, as the server resolves them: a forwarded host or client address counts only as the
 * configuration of the host and client address resolvers tells them, and the time is the time
 * of the {@link Clock} bean.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteServerConditionsTest {
    public static final String SPEC_NAME = "HandlerRouteServerConditionsTest";
    private static final Instant NOON = Instant.parse("2026-09-27T12:00:00Z");
    private static final Map<String, Object> CONFIGURATION = Map.of(
        "micronaut.server.host-resolution.host-header", "X-Gateway-Host",
        "micronaut.server.client-address-header", "X-Gateway-Client"
    );

    @Test
    void theHostOfTheRequestSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/conditions/host").header("X-Gateway-Host", "eu.api.example.com"), "example");
            assertBody(server, HttpRequest.GET("/conditions/host").header("X-Gateway-Host", "api.example.org:8443"), "other");
            // the forwarded host is read only as the host resolution is configured
            assertBody(server, HttpRequest.GET("/conditions/host").header("X-Forwarded-Host", "api.example.com"), "other");
        }
    }

    @Test
    void theConfiguredHostHeaderSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // without the configured header, the host of the server
            assertBody(server, HttpRequest.GET("/conditions/local"), "local");
            // a forwarded host does not count, the configured header does
            assertBody(server, HttpRequest.GET("/conditions/local").header("X-Forwarded-Host", "api.example.com"), "local");
            assertBody(server, HttpRequest.GET("/conditions/local").header("X-Gateway-Host", "api.example.com"), "api");
        }
    }

    @Test
    void aCookieSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // first: a client may keep the cookies it sent, e.g. the JDK client in its cookie store
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/cookie"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            assertBody(server, HttpRequest.GET("/conditions/cookie").cookie(Cookie.of("variant", "b")), "variant b");
            assertBody(server, HttpRequest.GET("/conditions/cookie").cookie(Cookie.of("variant", "z")), "session");
        }
    }

    @Test
    void theClientAddressSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // the test client connects over the loopback interface: without the configured header, the peer
            assertBody(server, HttpRequest.GET("/conditions/address"), "loopback");
            // the client address header the server is configured with
            assertBody(server, HttpRequest.GET("/conditions/client").header("X-Gateway-Client", "198.51.100.7"), "partner");
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/client").header("X-Gateway-Client", "203.0.113.7"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            // a forwarded address does not count when another header is configured
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/client").header("X-Forwarded-For", "198.51.100.7"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    @Test
    void thePeerAddressIgnoresForwardedAndConfiguredHeaders() throws IOException {
        try (ServerUnderTest server = server()) {
            // spoofed addresses in the allowed range, from a loopback peer outside it
            AssertionUtils.assertThrows(server, HttpRequest.GET("/conditions/peer")
                .header("X-Forwarded-For", "198.51.100.7")
                .header("X-Gateway-Client", "198.51.100.7"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            // the resolved address trusts the configured header, the peer address does not
            assertBody(server, HttpRequest.GET("/conditions/client").header("X-Gateway-Client", "198.51.100.7"), "partner");
            assertBody(server, HttpRequest.GET("/conditions/admin").header("X-Gateway-Client", "203.0.113.7")
                .header("X-Forwarded-For", "203.0.113.7"), "admin");
        }
    }

    @Test
    void theTimeOfTheRequestSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/conditions/time"), "during");
            assertBody(server, HttpRequest.GET("/conditions/launch"), "launched");
        }
    }

    private static void assertBody(ServerUnderTest server, HttpRequest<?> request, String body) {
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .body(body)
            .build());
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, CONFIGURATION);
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ClockFactory {
        @Singleton
        Clock clock() {
            return Clock.fixed(NOON.plusSeconds(30), ZoneOffset.UTC);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class ServerConditionRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.GET("/conditions/host")
                .where(host(endsWith(".example.com")))
                .handle((request, pathVariables) -> text("example"));
            routes.GET("/conditions/host")
                .where(host(endsWith(".example.com")).negate())
                .handle((request, pathVariables) -> text("other"));

            routes.GET("/conditions/local")
                .where(host("localhost", "127.0.0.1", "[::1]"))
                .handle((request, pathVariables) -> text("local"));
            routes.GET("/conditions/local")
                .where(host("api.example.com"))
                .handle((request, pathVariables) -> text("api"));

            routes.GET("/conditions/cookie")
                .where(cookie("variant", regex("[a-c]")))
                .handle((request, pathVariables) -> text("variant " + request.getCookies().get("variant").getValue()));
            routes.GET("/conditions/cookie")
                .where(cookie("variant").and(cookie("variant", regex("[a-c]")).negate()))
                .handle((request, pathVariables) -> text("session"));

            routes.GET("/conditions/address")
                .where(remoteAddress("127.0.0.0/8", "::1"))
                .handle((request, pathVariables) -> text("loopback"));
            routes.GET("/conditions/address")
                .where(remoteAddress("127.0.0.0/8", "::1").negate())
                .handle((request, pathVariables) -> text("remote"));
            routes.GET("/conditions/client")
                .where(remoteAddress("198.51.100.0/24"))
                .handle((request, pathVariables) -> text("partner"));

            routes.GET("/conditions/peer")
                .where(peerAddress("198.51.100.0/24"))
                .handle((request, pathVariables) -> text("peer partner"));
            routes.GET("/conditions/admin")
                .where(peerAddress("127.0.0.0/8", "::1"))
                .handle((request, pathVariables) -> text("admin"));

            routes.GET("/conditions/time")
                .where(before(NOON))
                .handle((request, pathVariables) -> text("before"));
            routes.GET("/conditions/time")
                .where(between(NOON, NOON.plusSeconds(60)))
                .handle((request, pathVariables) -> text("during"));
            routes.GET("/conditions/time")
                .where(after(NOON.plusSeconds(60)))
                .handle((request, pathVariables) -> text("after"));
            routes.GET("/conditions/launch")
                .where(after(Instant.EPOCH))
                .handle((request, pathVariables) -> text("launched"));
            routes.GET("/conditions/launch")
                .where(before(Instant.EPOCH))
                .handle((request, pathVariables) -> text("pending"));
        }
    }
}
