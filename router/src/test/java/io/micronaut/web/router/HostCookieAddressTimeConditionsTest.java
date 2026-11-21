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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.RequestConditions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Function;

import static io.micronaut.web.router.builder.RequestCondition.after;
import static io.micronaut.web.router.builder.RequestCondition.before;
import static io.micronaut.web.router.builder.RequestCondition.between;
import static io.micronaut.web.router.builder.RequestCondition.cookie;
import static io.micronaut.web.router.builder.RequestCondition.host;
import static io.micronaut.web.router.builder.RequestCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.endsWith;
import static io.micronaut.web.router.builder.ValueMatcher.equalTo;
import static io.micronaut.web.router.builder.ValueMatcher.present;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The host, cookie, client address and time conditions of {@link RequestCondition}.
 */
class HostCookieAddressTimeConditionsTest {

    private static final Instant NOON = Instant.parse("2026-09-27T12:00:00Z");

    @Test
    void hostConditions() {
        assertTrue(p(host("api.example.com")).test(withHost("api.example.com")));
        assertTrue(p(host("api.example.com")).test(withHost("API.Example.COM:8443")), "ignoring case and the port");
        assertTrue(p(host("api.example.com")).test(withHost("api.example.com.")), "the final dot of a fully qualified name");
        assertFalse(p(host("api.example.com")).test(withHost("example.com")));

        RequestCondition subdomains = host(endsWith(".example.com"));
        assertTrue(p(subdomains).test(withHost("api.example.com")));
        assertTrue(p(subdomains).test(withHost("eu.api.example.com")), "a suffix spans dots");
        assertFalse(p(subdomains).test(withHost("example.com")), "a subdomain has a label before the suffix");
        assertFalse(p(subdomains).test(withHost("api.example.org")));
        assertFalse(p(subdomains).test(withHost("api-example.com")), "a dot is a literal dot");

        assertTrue(p(host(regex("api\\..+\\.example\\.com"))).test(withHost("api.eu.example.com")));
        assertTrue(p(host(present())).test(withHost("anything")));
        assertTrue(p(host("one.test", "two.test")).test(withHost("two.test")), "one of the hosts");
        assertTrue(p(host("[::1]")).test(withHost("[::1]:8080")), "an IPv6 address in brackets");
        assertTrue(p(host("127.0.0.1")).test(withHost("127.0.0.1:8080")));
        assertFalse(p(host(present())).test(HttpRequest.GET("/x")), "no Host header and a relative URI");
        assertTrue(p(host("example.com")).test(HttpRequest.GET("https://example.com/x")), "the host of an absolute URI without a Host header");
    }

    @Test
    void theHostIgnoresForwardedHeadersUnlessTheServerResolvesThem() {
        MutableHttpRequest<?> request = withHost("internal.local").header("X-Forwarded-Host", "api.example.com");
        assertFalse(p(host("api.example.com")).test(request));

        RouteConditionContext forwarded = context(r -> "https://" + r.getHeaders().get("X-Forwarded-Host") + ":8443", null, null);
        assertTrue(RequestConditions.matches(host("api.example.com"), request, forwarded), "the host of the resolved URI");
        assertTrue(RequestConditions.matches(host(endsWith(".example.com")), request, context(r -> "api.example.com:80", null, null)));
        assertFalse(RequestConditions.matches(host(present()), request, context(r -> null, null, null)), "no resolved host");
    }

    @Test
    void invalidHosts() {
        assertThrows(IllegalArgumentException.class, RequestCondition::host);
        assertThrows(NullPointerException.class, () -> host((String) null));
    }

    @Test
    void cookieConditions() {
        MutableHttpRequest<?> request = HttpRequest.GET("/x").cookie(Cookie.of("variant", "b")).cookie(Cookie.of("SESSION", "abc123"));

        assertTrue(p(cookie("variant")).test(request));
        assertFalse(p(cookie("Variant")).test(request), "the name is case-sensitive");
        assertFalse(p(cookie("other")).test(request));
        assertTrue(p(cookie("variant", regex("a|b"))).test(request));
        assertTrue(p(cookie("SESSION", regex("[a-z]+\\d+"))).test(request));
        assertFalse(p(cookie("SESSION", regex("[a-z]+"))).test(request), "the whole value matches");
        assertFalse(p(cookie("other", regex(".*"))).test(request));
        assertFalse(p(cookie("variant")).test(HttpRequest.GET("/x")));
    }

    @Test
    void remoteAddressOfTheConnection() {
        RequestCondition privateNetworks = remoteAddress("10.0.0.0/8", "192.168.0.0/16", "fd00::/8");
        assertTrue(p(privateNetworks).test(from("10.1.2.3")));
        assertTrue(p(privateNetworks).test(from("192.168.200.1")));
        assertFalse(p(privateNetworks).test(from("11.0.0.1")));
        assertFalse(p(privateNetworks).test(from("192.169.0.1")));
        assertTrue(p(privateNetworks).test(from("fd12:3456::1")));
        assertFalse(p(privateNetworks).test(from("2001:db8::1")));
        assertTrue(p(privateNetworks).test(from("::ffff:10.0.0.1")), "an IPv4 address mapped to IPv6");

        assertTrue(p(remoteAddress("192.168.1.7")).test(from("192.168.1.7")), "a single address");
        assertFalse(p(remoteAddress("192.168.1.7")).test(from("192.168.1.8")));
        assertTrue(p(remoteAddress("0.0.0.0/0")).test(from("203.0.113.9")), "every IPv4 address");
        assertFalse(p(remoteAddress("0.0.0.0/0")).test(from("2001:db8::1")), "an IPv4 range has no IPv6 address");
        assertTrue(p(remoteAddress("::/0")).test(from("2001:db8::1")));
        assertTrue(p(remoteAddress("::1")).test(from("::1")));
        assertTrue(p(remoteAddress("[2001:db8::]/32")).test(from("2001:db8:ffff::1")), "an IPv6 range in brackets");
        assertTrue(p(remoteAddress("10.1.2.3/8")).test(from("10.200.0.1")), "the host bits of the network are ignored");
        assertTrue(p(remoteAddress("172.16.0.0/12")).test(from("172.31.255.255")));
        assertFalse(p(remoteAddress("172.16.0.0/12")).test(from("172.32.0.0")));
        assertTrue(p(remoteAddress("::ffff:10.0.0.0/104")).test(from("10.9.9.9")), "a range of IPv4 addresses mapped to IPv6");
    }

    @Test
    void remoteAddressAsTheServerResolvesIt() {
        RequestCondition internal = remoteAddress("10.0.0.0/8", "2001:db8::/32");
        RouteConditionContext resolving = context(null, r -> r.getHeaders().get("X-Client"), null);
        assertTrue(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "10.0.0.1"), resolving));
        assertTrue(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "10.0.0.1:4711"), resolving), "an address with a port");
        assertTrue(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "[2001:db8::1]:4711"), resolving));
        assertTrue(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "2001:db8::1"), resolving));
        assertFalse(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "192.0.2.1"), resolving));
        assertFalse(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "unknown"), resolving), "not an address");
        assertFalse(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "_hidden"), resolving), "an obfuscated identifier");
        assertFalse(RequestConditions.matches(internal, HttpRequest.GET("/x").header("X-Client", "localhost"), resolving), "a name is not looked up");
        assertFalse(RequestConditions.matches(internal, HttpRequest.GET("/x"), resolving), "no resolved address");
    }

    @Test
    void invalidAddressRanges() {
        assertThrows(IllegalArgumentException.class, RequestCondition::remoteAddress);
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("localhost"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("example.com/24"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("10.0.0.0/33"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("10.0.0.0/-1"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("10.0.0.0/x"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("256.0.0.0/8"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("10.0.0/8"));
        assertThrows(IllegalArgumentException.class, () -> remoteAddress("::/129"));
    }

    @Test
    void timeConditions() {
        RouteConditionContext beforeNoon = clock(NOON.minusSeconds(1));
        RouteConditionContext atNoon = clock(NOON);
        HttpRequest<?> request = HttpRequest.GET("/x");

        assertTrue(RequestConditions.matches(before(NOON), request, beforeNoon));
        assertFalse(RequestConditions.matches(before(NOON), request, atNoon), "the instant is excluded");
        assertFalse(RequestConditions.matches(after(NOON), request, beforeNoon));
        assertTrue(RequestConditions.matches(after(NOON), request, atNoon), "the instant is included");

        Instant end = NOON.plusSeconds(3600);
        assertFalse(RequestConditions.matches(between(NOON, end), request, beforeNoon));
        assertTrue(RequestConditions.matches(between(NOON, end), request, atNoon), "the start is included");
        assertTrue(RequestConditions.matches(between(NOON, end), request, clock(end.minusMillis(1))));
        assertFalse(RequestConditions.matches(between(NOON, end), request, clock(end)), "the end is excluded");
        assertFalse(RequestConditions.matches(between(NOON, NOON), request, atNoon), "an empty period");
        assertThrows(IllegalArgumentException.class, () -> between(end, NOON));

        assertTrue(p(after(Instant.EPOCH)).test(request), "the system clock");
        assertTrue(p(before(Instant.now().plusSeconds(3600))).test(request));
        assertTrue(p(between(Instant.EPOCH, Instant.now().plusSeconds(3600))).test(request));
    }

    @Test
    void theConditionsSelectAHandlerRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri);
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/tenant")
            .where(host(endsWith(".example.com")).and(cookie("beta", equalTo("on"))))
            .handle((request, pathVariables) -> HttpResponse.ok("tenant"));
        routes.GET("/tenant")
            .where(remoteAddress("10.0.0.0/8"))
            .handle((request, pathVariables) -> HttpResponse.ok("internal"));
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        UriRouteMatch<?, ?> tenant = router.findClosest(withHost("acme.example.com").cookie(Cookie.of("beta", "on")));
        assertNotNull(tenant);
        assertEquals(1, router.findAllClosest(withHost("acme.example.com").cookie(Cookie.of("beta", "on"))).size());
        assertNull(router.findClosest(withHost("acme.example.org").cookie(Cookie.of("beta", "on"))));
        assertNull(router.findClosest(withHost("acme.example.com")));
        assertNotNull(router.findClosest(new RemoteRequest(HttpRequest.GET("/tenant"), "10.0.0.5")));
    }

    private static java.util.function.Predicate<HttpRequest<?>> p(RequestCondition condition) {
        return request -> RequestConditions.matches(condition, request, RouteConditionContext.fallback());
    }

    private static MutableHttpRequest<?> withHost(String host) {
        return HttpRequest.GET("/tenant").header("Host", host);
    }

    private static HttpRequest<?> from(String address) {
        return new RemoteRequest(HttpRequest.GET("/x"), address);
    }

    private static RouteConditionContext clock(Instant now) {
        return context(null, null, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static RouteConditionContext context(@Nullable Function<HttpRequest<?>, String> host,
                                                 @Nullable Function<HttpRequest<?>, String> address,
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
