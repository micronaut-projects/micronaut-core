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
import io.micronaut.http.HttpMethod;
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
import io.micronaut.web.router.builder.RouteCondition;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import static io.micronaut.web.router.builder.RouteCondition.after;
import static io.micronaut.web.router.builder.RouteCondition.all;
import static io.micronaut.web.router.builder.RouteCondition.any;
import static io.micronaut.web.router.builder.RouteCondition.before;
import static io.micronaut.web.router.builder.RouteCondition.cookie;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static io.micronaut.web.router.builder.RouteCondition.host;
import static io.micronaut.web.router.builder.RouteCondition.method;
import static io.micronaut.web.router.builder.RouteCondition.not;
import static io.micronaut.web.router.builder.RouteCondition.peerAddress;
import static io.micronaut.web.router.builder.RouteCondition.query;
import static io.micronaut.web.router.builder.RouteCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.equalTo;
import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static io.micronaut.web.router.builder.ValueMatcher.startsWith;

/**
 * The declarative conditions of handler routes, {@link RouteCondition}, and their combinations:
 * the headers, query parameters, cookies and methods of a request, its host and client address
 * as the server resolves them, and the time.
 */
@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class HandlerRouteConditionCombinationsTest {
    public static final String SPEC_NAME = "HandlerRouteConditionCombinationsTest";

    @Test
    void combinedHeaderAndQueryConditionsSelectARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/combinations/search").header("X-Channel", "BETA"), "beta");
            assertBody(server, HttpRequest.GET("/combinations/search?channel=canary-2"), "beta");
            assertBody(server, HttpRequest.GET("/combinations/search").header("X-Channel", "beta").header("X-Legacy", "1"), "stable");
            assertBody(server, HttpRequest.GET("/combinations/search?channel=stable"), "stable");
            assertBody(server, HttpRequest.GET("/combinations/search"), "stable");
        }
    }

    @Test
    void theConditionsOfAGroupAndOfItsRoutesMustAllBeMet() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/combinations/admin/report?format=csv").header("X-Tenant", "acme"), "csv report");
            AssertionUtils.assertThrows(server, HttpRequest.GET("/combinations/admin/report?format=csv"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            // the POST route of the path meets the conditions of the group for this request
            AssertionUtils.assertThrows(server, HttpRequest.GET("/combinations/admin/report?format=pdf").header("X-Tenant", "acme"), HttpResponseAssertion.builder()
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .build());
            // the method condition of the group: a write needs a token
            AssertionUtils.assertThrows(server, HttpRequest.POST("/combinations/admin/report", "x").header("X-Tenant", "acme"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            assertBody(server, HttpRequest.POST("/combinations/admin/report", "x").header("X-Tenant", "acme").header("X-Write-Token", "t"), "saved");
        }
    }

    @Test
    void aCookieSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // first: a client may keep the cookies it sent, e.g. the JDK client in its cookie store
            assertBody(server, HttpRequest.GET("/combinations/variant"), "default");
            assertBody(server, HttpRequest.GET("/combinations/variant").cookie(Cookie.of("variant", "b")), "variant b");
            assertBody(server, HttpRequest.GET("/combinations/variant").cookie(Cookie.of("variant", "z")), "default");
        }
    }

    @Test
    void theHostAsTheServerResolvesItSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // without a configuration the host resolver reads a forwarded host
            assertBody(server, HttpRequest.GET("/combinations/host").header("X-Forwarded-Host", "EU.api.example.com"), "example");
            assertBody(server, HttpRequest.GET("/combinations/host").header("X-Forwarded-Host", "api.example.org:8443"), "other");
        }
    }

    @Test
    void theClientAddressAsTheServerResolvesItSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            // the test client connects over the loopback interface
            assertBody(server, HttpRequest.GET("/combinations/address"), "loopback");
            // without a configuration the client address resolver reads a forwarded address
            assertBody(server, HttpRequest.GET("/combinations/address").header("X-Forwarded-For", "198.51.100.7"), "partner");
            assertBody(server, HttpRequest.GET("/combinations/address").header("X-Forwarded-For", "203.0.113.7"), "remote");
        }
    }

    @Test
    void thePeerAddressIgnoresAForwardedAddress() throws IOException {
        try (ServerUnderTest server = server()) {
            // a spoofed forwarded address in the range, from a loopback peer outside it
            AssertionUtils.assertThrows(server, HttpRequest.GET("/combinations/peer").header("X-Forwarded-For", "198.51.100.7"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
            // without a configured client address header, the resolved address trusts it
            assertBody(server, HttpRequest.GET("/combinations/resolved").header("X-Forwarded-For", "198.51.100.7"), "resolved partner");
            assertBody(server, HttpRequest.GET("/combinations/loopback-peer").header("X-Forwarded-For", "198.51.100.7"), "loopback peer");
        }
    }

    @Test
    void theTimeOfTheRequestSelectsARoute() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/combinations/launch"), "launched");
        }
    }

    @Test
    void aMatcherConstrainsAPathVariable() throws IOException {
        try (ServerUnderTest server = server()) {
            assertBody(server, HttpRequest.GET("/combinations/prefixed/item-7"), "item-7");
            AssertionUtils.assertThrows(server, HttpRequest.GET("/combinations/prefixed/other"), HttpResponseAssertion.builder()
                .status(HttpStatus.NOT_FOUND)
                .build());
        }
    }

    private static void assertBody(ServerUnderTest server, HttpRequest<?> request, String body) {
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .body(body)
            .build());
    }

    private static ServerUnderTest server() {
        return ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, Map.of());
    }

    private static HttpResponse<?> text(String body) {
        return HttpResponse.ok(body).contentType(MediaType.TEXT_PLAIN_TYPE);
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class CombinationRoutes implements HttpRoutes {

        @Override
        public void routes(HttpRouteBuilder routes) {
            RouteCondition beta = any(
                header("X-Channel", oneOf("beta", "canary").ignoringCase()),
                query("channel", regex("(beta|canary)(-\\d+)?")));
            routes.GET("/combinations/search")
                .where(beta.and(not(header("X-Legacy"))))
                .handle((request, pathVariables) -> text("beta"));
            routes.GET("/combinations/search")
                .where(not(beta.and(not(header("X-Legacy")))))
                .handle((request, pathVariables) -> text("stable"));

            routes.path("/combinations/admin", admin -> {
                admin.where(header("X-Tenant", equalTo("acme")));
                admin.where(method(HttpMethod.GET).or(header("X-Write-Token")));
                admin.GET("/report")
                    .where(query("format", "csv"))
                    .handle((request, pathVariables) -> text("csv report"));
                admin.POST("/report", (request, pathVariables) -> text("saved"));
            });

            routes.GET("/combinations/variant")
                .where(cookie("variant", regex("[a-c]")))
                .handle((request, pathVariables) -> text("variant " + request.getCookies().get("variant").getValue()));
            routes.GET("/combinations/variant")
                .where(not(cookie("variant", regex("[a-c]"))))
                .handle((request, pathVariables) -> text("default"));

            routes.GET("/combinations/host")
                .where(host(endsWith(".example.com")))
                .handle((request, pathVariables) -> text("example"));
            routes.GET("/combinations/host")
                .where(not(host(endsWith(".example.com"))))
                .handle((request, pathVariables) -> text("other"));

            RouteCondition loopback = remoteAddress("127.0.0.0/8", "::1");
            RouteCondition partner = remoteAddress("198.51.100.0/24");
            routes.GET("/combinations/address")
                .where(loopback)
                .handle((request, pathVariables) -> text("loopback"));
            routes.GET("/combinations/address")
                .where(partner)
                .handle((request, pathVariables) -> text("partner"));
            routes.GET("/combinations/address")
                .where(all(not(loopback), not(partner)))
                .handle((request, pathVariables) -> text("remote"));

            routes.GET("/combinations/peer")
                .where(peerAddress("198.51.100.0/24"))
                .handle((request, pathVariables) -> text("peer partner"));
            routes.GET("/combinations/resolved")
                .where(remoteAddress("198.51.100.0/24"))
                .handle((request, pathVariables) -> text("resolved partner"));
            routes.GET("/combinations/loopback-peer")
                .where(peerAddress("127.0.0.0/8", "::1"))
                .handle((request, pathVariables) -> text("loopback peer"));

            routes.GET("/combinations/launch")
                .where(after(Instant.EPOCH).and(header("X-Never").negate()))
                .handle((request, pathVariables) -> text("launched"));
            routes.GET("/combinations/launch")
                .where(before(Instant.EPOCH))
                .handle((request, pathVariables) -> text("pending"));
            // a lambda combined with the declarative conditions
            routes.GET("/combinations/launch")
                .where(header("X-Never").and(request -> request.getPath().startsWith("/combinations")))
                .handle((request, pathVariables) -> text("never"));
            routes.GET("/combinations/prefixed/{name}")
                .constrain("name", startsWith("item-"))
                .handle((request, pathVariables) -> text(pathVariables.getString("name")));
        }
    }
}
