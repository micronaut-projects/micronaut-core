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
package io.micronaut.web.router;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.web.router.builder.Cidr;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.RequestPredicates;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.RouteConditions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static io.micronaut.web.router.builder.RouteCondition.after;
import static io.micronaut.web.router.builder.RouteCondition.all;
import static io.micronaut.web.router.builder.RouteCondition.any;
import static io.micronaut.web.router.builder.RouteCondition.before;
import static io.micronaut.web.router.builder.RouteCondition.between;
import static io.micronaut.web.router.builder.RouteCondition.cookie;
import static io.micronaut.web.router.builder.RouteCondition.custom;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static io.micronaut.web.router.builder.RouteCondition.host;
import static io.micronaut.web.router.builder.RouteCondition.method;
import static io.micronaut.web.router.builder.RouteCondition.not;
import static io.micronaut.web.router.builder.RouteCondition.query;
import static io.micronaut.web.router.builder.RouteCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.equalTo;
import static io.micronaut.web.router.builder.ValueMatcher.oneOf;
import static io.micronaut.web.router.builder.ValueMatcher.present;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static io.micronaut.web.router.builder.ValueMatcher.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link RouteCondition} conditions: their evaluation, the conditions of the server, and the
 * routes they select.
 */
class RouteConditionTest {

    private static final Instant NOON = Instant.parse("2026-09-27T12:00:00Z");

    @Test
    void headerConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").header("X-Channel", "stable").header("X-Channel", "Beta-2");

        assertTrue(header("x-channel").test(request), "the name is case-insensitive");
        assertFalse(header("X-Other").test(request));
        assertTrue(header("X-Channel", "stable").test(request));
        assertTrue(header("X-Channel", startsWith("beta").ignoringCase()).test(request), "any of the values");
        assertFalse(header("X-Channel", oneOf("alpha", "gamma")).test(request));
        assertTrue(header("X-Other", present().negate()).test(request), "an absent header is an absent value");
        assertFalse(header("X-Other", equalTo("a")).test(request));
    }

    @Test
    void queryConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x");
        request.getParameters().add("format", List.of("csv", "json")).add("debug", List.of(""));

        assertTrue(query("debug").test(request), "a parameter without a value");
        assertTrue(query("debug", equalTo("")).test(request));
        assertFalse(query("Format").test(request), "the name is case-sensitive");
        assertTrue(query("format", "json").test(request), "any of the values");
        assertTrue(query("format", regex("[a-z]{3}")).test(request));
        assertFalse(query("format", "xml").test(request));
    }

    @Test
    void cookiesAreScannedInTheCookieHeader() {
        HttpRequest<?> request = HttpRequest.GET("/x")
            .header(HttpHeaders.COOKIE, "SESSION=abc123; variant=\"b\";flag; empty=")
            .header(HttpHeaders.COOKIE, "late = x ");

        assertTrue(cookie("variant").test(request));
        assertTrue(cookie("variant", equalTo("b")).test(request), "without the quotes of a quoted value");
        assertTrue(cookie("SESSION", regex("[a-z]+\\d+")).test(request));
        assertFalse(cookie("SESSION", regex("[a-z]+")).test(request), "the whole value");
        assertFalse(cookie("session").test(request), "the name is case-sensitive");
        assertFalse(cookie("SESS").test(request), "the whole name");
        assertTrue(cookie("flag", equalTo("")).test(request), "a cookie without a value");
        assertTrue(cookie("empty", equalTo("")).test(request));
        assertTrue(cookie("late", equalTo("x")).test(request), "another Cookie header, trimmed");
        assertTrue(cookie("missing", present().negate()).test(request));
        assertFalse(cookie("missing").test(request));

        HttpRequest<?> built = HttpRequest.GET("/x").cookie(Cookie.of("variant", "c"));
        assertTrue(cookie("variant", oneOf("b", "c")).test(built), "the cookies of a request built in code");
        assertFalse(cookie("variant").test(HttpRequest.GET("/x")));
    }

    @Test
    void methodConditions() {
        assertTrue(method(HttpMethod.GET, HttpMethod.HEAD).test(HttpRequest.HEAD("/x")));
        assertFalse(method(HttpMethod.GET).test(HttpRequest.POST("/x", "")));
        assertTrue(method("PURGE").test(HttpRequest.create(HttpMethod.CUSTOM, "/x", "PURGE")), "a custom method by name");
        assertTrue(method(HttpMethod.CUSTOM).test(HttpRequest.create(HttpMethod.CUSTOM, "/x", "PURGE")), "every custom method");
        assertFalse(method("PURGE").test(HttpRequest.create(HttpMethod.CUSTOM, "/x", "LOCK")));
        assertThrows(IllegalArgumentException.class, () -> method(new String[0]));
    }

    @Test
    void hostConditionsReadTheHostAsTheServerResolvesIt() {
        assertTrue(host("api.example.com").test(withHost("API.Example.COM:8443")), "ignoring case and the port");
        assertTrue(host("api.example.com").test(withHost("api.example.com.")), "the final dot of a fully qualified name");
        assertTrue(host(endsWith(".example.com")).test(withHost("eu.api.example.com")));
        assertFalse(host(endsWith(".example.com")).test(withHost("example.com")));
        assertTrue(host("[::1]").test(withHost("[::1]:8080")), "an IPv6 address in brackets");
        assertTrue(host("one.test", "two.test").test(withHost("two.test")));
        assertFalse(host(present()).test(HttpRequest.GET("/x")), "no Host header and a relative URI");
        assertTrue(host("example.com").test(HttpRequest.GET("https://example.com/x")), "the host of an absolute URI");

        // the server resolves the host, e.g. from a forwarded header, as a URI
        RouteConditionContext resolving = context(request -> "https://API.example.com.:8443", null, null);
        RouteCondition api = host(equalTo("api.example.com"));
        assertTrue(RouteConditions.matches(api, withHost("internal.local"), resolving));
        assertFalse(RouteConditions.matches(api, withHost("internal.local"), RouteConditionContext.fallback()));
        assertFalse(RouteConditions.matches(api, withHost("api.example.com"), context(request -> null, null, null)), "no resolved host");
    }

    @Test
    void remoteAddressConditionsReadTheAddressAsTheServerResolvesIt() {
        RouteCondition privateNetworks = remoteAddress("10.0.0.0/8", "192.168.0.0/16", "fd00::/8");
        assertTrue(privateNetworks.test(from("10.1.2.3")), "the peer of the connection");
        assertFalse(privateNetworks.test(from("11.0.0.1")));
        assertTrue(privateNetworks.test(from("fd12:3456::1")));
        assertTrue(privateNetworks.test(from("::ffff:10.0.0.1")), "an IPv4 address mapped to IPv6");
        assertFalse(privateNetworks.test(HttpRequest.GET("/x")), "no address");

        RouteConditionContext resolving = context(null, request -> request.getHeaders().get("X-Client"), null);
        assertTrue(RouteConditions.matches(privateNetworks, HttpRequest.GET("/x").header("X-Client", "10.0.0.1:4711"), resolving));
        assertTrue(RouteConditions.matches(privateNetworks, HttpRequest.GET("/x").header("X-Client", "[fd00::1]:4711"), resolving));
        assertFalse(RouteConditions.matches(privateNetworks, HttpRequest.GET("/x").header("X-Client", "unknown"), resolving), "not an address");
        assertFalse(RouteConditions.matches(privateNetworks, HttpRequest.GET("/x").header("X-Client", "localhost"), resolving), "a name is not looked up");

        assertEquals("10.0.0.0/8", Cidr.parse("10.1.2.3/8").toString(), "the host bits are cleared");
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("example.com/24"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, RouteCondition::remoteAddress);
    }

    @Test
    void timeWindowsUseTheClockOfTheServer() {
        HttpRequest<?> request = HttpRequest.GET("/x");
        RouteConditionContext beforeNoon = context(null, null, Clock.fixed(NOON.minusSeconds(1), ZoneOffset.UTC));
        RouteConditionContext atNoon = context(null, null, Clock.fixed(NOON, ZoneOffset.UTC));

        assertTrue(RouteConditions.matches(before(NOON), request, beforeNoon));
        assertFalse(RouteConditions.matches(before(NOON), request, atNoon), "the end is excluded");
        assertFalse(RouteConditions.matches(after(NOON), request, beforeNoon));
        assertTrue(RouteConditions.matches(after(NOON), request, atNoon), "the start is included");
        assertTrue(RouteConditions.matches(between(NOON, NOON.plusSeconds(60)), request, atNoon));
        assertFalse(RouteConditions.matches(between(NOON, NOON), request, atNoon), "an empty period");
        assertThrows(IllegalArgumentException.class, () -> between(NOON, NOON.minusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> new RouteCondition.TimeWindow(null, null));

        assertTrue(after(Instant.EPOCH).test(request), "the system clock");
    }

    @Test
    void combinationsAndLambdas() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").header("X-A", "1");

        assertTrue(all().test(request));
        assertFalse(any().test(request));
        assertTrue(all(header("X-A"), method(HttpMethod.GET)).test(request));
        assertTrue(any(header("X-B"), header("X-A")).test(request));
        assertTrue(not(header("X-B")).test(request));
        assertTrue(header("X-A").and(r -> r.getPath().equals("/x")).test(request), "with a lambda");
        assertTrue(header("X-B").or(r -> true).test(request));
        assertInstanceOf(RouteCondition.AllOf.class, header("X-A").and(header("X-B")));
        RouteCondition withLambda = header("X-A").or(r -> true);
        assertInstanceOf(RouteCondition.Custom.class, assertInstanceOf(RouteCondition.AnyOf.class, withLambda).conditions().get(1), "a lambda is a custom condition");
        RouteCondition condition = header("X-A");
        assertSame(condition, custom(condition), "a condition given as a predicate is that condition");
        assertSame(condition, condition.negate().negate());

        // a condition is a predicate: the RequestPredicates of before compose as they did
        Predicate<HttpRequest<?>> composed = RequestPredicates.header("X-A").and(RequestPredicates.header("X-B").negate())
            .or(RequestPredicates.queryParam("force"));
        assertTrue(composed.test(request));
        assertInstanceOf(RouteCondition.class, composed);
        assertInstanceOf(RouteCondition.Custom.class, RequestPredicates.accept(MediaType.TEXT_CSV_TYPE));
        assertEquals(header("X-A"), RequestPredicates.header("X-A"));
        assertEquals(query("format", "csv"), RequestPredicates.queryParam("format", "csv"));
    }

    @Test
    void theConditionsSelectAHandlerRoute() {
        Router router = router(routes -> {
            routes.GET("/tenant")
                .where(host(endsWith(".example.com")).and(cookie("beta", equalTo("on"))))
                .where(header("X-Tenant"))
                .handle((request, pathVariables) -> HttpResponse.ok("beta"));
            routes.GET("/tenant")
                .where(remoteAddress("10.0.0.0/8"))
                .handle((request, pathVariables) -> HttpResponse.ok("internal"));
        });

        assertNotNull(router.findClosest(withHost("acme.example.com").header("X-Tenant", "a").cookie(Cookie.of("beta", "on"))));
        assertNull(router.findClosest(withHost("acme.example.com").cookie(Cookie.of("beta", "on"))), "several where calls must all be met");
        assertNull(router.findClosest(withHost("acme.example.org").header("X-Tenant", "a").cookie(Cookie.of("beta", "on"))));
        assertNotNull(router.findClosest(new RemoteRequest(HttpRequest.GET("/tenant"), "10.0.0.5")));
    }

    @Test
    void theRouteInfoShowsTheNormalizedConditions() {
        Predicate<HttpRequest<?>> lambda = request -> true;
        Router router = router(routes -> routes.path("/beta", beta -> {
            beta.where(RouteCondition.custom(lambda).and(header("X-Beta")));
            beta.GET("/search")
                .where(method(HttpMethod.GET))
                .where(query("q", regex("\\w+")))
                .handle((request, pathVariables) -> HttpResponse.ok("search"));
            beta.GET("/plain", (request, pathVariables) -> HttpResponse.ok("plain"));
        }));

        MutableHttpRequest<?> search = HttpRequest.GET("/beta/search").header("X-Beta", "1");
        search.getParameters().add("q", List.of("abc"));
        UriRouteMatch<Object, Object> match = router.findClosest(search);
        assertNotNull(match);
        assertEquals(List.of(method(HttpMethod.GET), header("X-Beta"), query("q", regex("\\w+")), new RouteCondition.Custom(lambda)),
            match.getRouteInfo().getConditions(), "the group's and the route's conditions, cheapest first, the lambda last");

        UriRouteMatch<Object, Object> plain = router.findClosest(HttpRequest.GET("/beta/plain").header("X-Beta", "1"));
        assertNotNull(plain);
        assertEquals(List.of(header("X-Beta"), new RouteCondition.Custom(lambda)), plain.getRouteInfo().getConditions());

        Router unconditioned = router(routes -> routes.GET("/free", (request, pathVariables) -> HttpResponse.ok("free")));
        UriRouteMatch<Object, Object> free = unconditioned.findClosest(HttpRequest.GET("/free"));
        assertNotNull(free);
        assertEquals(List.of(), free.getRouteInfo().getConditions());
    }

    private static Router router(Consumer<HttpRouteBuilder> routes) {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        routes.accept(new DefaultHttpRouteBuilder(assembly));
        assembly.addImplicitHeadRoutes();
        return new DefaultRouter(List.of(), List.of(() -> assembly));
    }

    private static MutableHttpRequest<?> withHost(String host) {
        return HttpRequest.GET("/tenant").header(HttpHeaders.HOST, host);
    }

    private static HttpRequest<?> from(String address) {
        return new RemoteRequest(HttpRequest.GET("/x"), address);
    }

    private static RouteConditionContext context(java.util.function.@Nullable Function<HttpRequest<?>, String> host,
                                                 java.util.function.@Nullable Function<HttpRequest<?>, String> address,
                                                 @Nullable Clock clock) {
        return new RouteConditionContext() {
            @Override
            public @Nullable String host(HttpRequest<?> request) {
                return host != null ? host.apply(request) : RouteConditionContext.fallback().host(request);
            }

            @Override
            public @Nullable String clientAddress(HttpRequest<?> request) {
                return address != null ? address.apply(request) : RouteConditionContext.fallback().clientAddress(request);
            }

            @Override
            public Clock clock() {
                return clock != null ? clock : Clock.systemUTC();
            }
        };
    }

    /**
     * A request from a client address.
     */
    private static final class RemoteRequest extends HttpRequestWrapper<Object> {
        private final InetSocketAddress remote;

        @SuppressWarnings("unchecked")
        RemoteRequest(HttpRequest<?> delegate, String address) {
            super((HttpRequest<Object>) delegate);
            this.remote = new InetSocketAddress(address, 50000);
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return remote;
        }
    }
}
