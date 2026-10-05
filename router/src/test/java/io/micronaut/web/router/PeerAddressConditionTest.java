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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.HttpResponse;
import io.micronaut.web.router.builder.Cidr;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.builder.RouteConditions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.List;

import static io.micronaut.web.router.builder.RouteCondition.all;
import static io.micronaut.web.router.builder.RouteCondition.any;
import static io.micronaut.web.router.builder.RouteCondition.custom;
import static io.micronaut.web.router.builder.RouteCondition.header;
import static io.micronaut.web.router.builder.RouteCondition.method;
import static io.micronaut.web.router.builder.RouteCondition.not;
import static io.micronaut.web.router.builder.RouteCondition.peerAddress;
import static io.micronaut.web.router.builder.RouteCondition.remoteAddress;
import static io.micronaut.web.router.builder.ValueMatcher.regex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RouteCondition#peerAddress(String...)}: the address of the peer of the connection only,
 * never a resolved or forwarded address.
 */
class PeerAddressConditionTest {

    @Test
    void thePeerOfTheConnectionMatches() {
        RouteCondition internal = peerAddress("10.0.0.0/8", "fd00::/8");
        assertTrue(meets(internal, from("10.1.2.3")));
        assertFalse(meets(internal, from("11.0.0.1")));
        assertTrue(meets(internal, from("fd12::1")));
        assertFalse(meets(internal, from("2001:db8::1")));
        assertTrue(meets(internal, from("::ffff:10.0.0.1")), "an IPv4 address mapped to IPv6");
        assertTrue(meets(peerAddress("192.168.1.7"), from("192.168.1.7")), "a single address");
    }

    @Test
    void noPeerOrAnUnresolvedPeerMatchesNothing() {
        assertFalse(meets(peerAddress("0.0.0.0/0", "::/0"), new RemoteRequest(HttpRequest.GET("/x"), null)), "no peer address");
        HttpRequest<?> unresolved = new RemoteRequest(HttpRequest.GET("/x"), InetSocketAddress.createUnresolved("10.0.0.1", 50000));
        assertFalse(meets(peerAddress("10.0.0.0/8"), unresolved), "an unresolved peer is never looked up");
        assertTrue(meets(not(peerAddress("10.0.0.0/8")), new RemoteRequest(HttpRequest.GET("/x"), null)), "negated, no peer meets the condition");
    }

    @Test
    void aForwardedOrResolvedAddressDoesNotCount() {
        HttpRequest<?> spoofed = new RemoteRequest(HttpRequest.GET("/x").header("X-Forwarded-For", "10.0.0.1"), new InetSocketAddress("203.0.113.9", 50000));
        RouteConditionContext resolving = new RouteConditionContext() {
            @Override
            public @Nullable String host(HttpRequest<?> request) {
                return null;
            }

            @Override
            public @Nullable String clientAddress(HttpRequest<?> request) {
                return request.getHeaders().get("X-Forwarded-For");
            }

            @Override
            public Clock clock() {
                return Clock.systemUTC();
            }
        };

        assertFalse(RouteConditions.matches(peerAddress("10.0.0.0/8"), spoofed, resolving), "the peer, not the forwarded address");
        assertTrue(RouteConditions.matches(remoteAddress("10.0.0.0/8"), spoofed, resolving), "the resolved address, which the header gives");
        assertTrue(RouteConditions.matches(peerAddress("203.0.113.0/24"), spoofed, resolving));
    }

    @Test
    void aScopedIpv6AddressMatchesWithoutItsZone() {
        assertNotNull(Cidr.address("fe80::1%en0"));
        assertNotNull(Cidr.address("[fe80::1%en0]:4711"));
        assertTrue(Cidr.parse("fe80::/10").contains(Cidr.address("fe80::1%en0")));
        HttpRequest<?> request = new RemoteRequest(HttpRequest.GET("/x"), null);
        RouteConditionContext scoped = new RouteConditionContext() {
            @Override
            public @Nullable String host(HttpRequest<?> request) {
                return null;
            }

            @Override
            public @Nullable String clientAddress(HttpRequest<?> request) {
                return "fe80::1%en0";
            }

            @Override
            public Clock clock() {
                return Clock.systemUTC();
            }
        };
        assertTrue(RouteConditions.matches(remoteAddress("fe80::/10"), request, scoped), "the zone id does not prevent a match");
        assertNull(Cidr.address("fe80::1%"), "an empty zone id");
        assertNull(Cidr.address("fe80::zz%en0"), "not hexadecimal before the zone id");
    }

    @Test
    void equalityNormalizationAndCost() {
        assertEquals(peerAddress("10.0.0.0/8"), peerAddress("10.1.2.3/8"), "equal ranges");
        assertEquals(new RouteCondition.PeerAddress(List.of(Cidr.parse("::1"))), peerAddress("::1"));
        assertNotEquals(peerAddress("10.0.0.0/8"), remoteAddress("10.0.0.0/8"), "not the resolved address");
        assertThrows(IllegalArgumentException.class, RouteCondition::peerAddress);
        assertThrows(IllegalArgumentException.class, () -> peerAddress("localhost"));
        assertThrows(IllegalArgumentException.class, () -> new RouteCondition.PeerAddress(List.of()));

        RouteCondition peer = peerAddress("10.0.0.0/8");
        assertEquals(peer, RouteConditions.normalize(not(not(peer))));
        assertEquals(peer, peer.negate().negate());
        assertEquals(peer, RouteConditions.normalize(all(peer, peerAddress("10.0.0.0/8"))), "a duplicate is removed");
        assertEquals(any(header("X-A"), peer), RouteConditions.normalize(any(peer, header("X-A"), peer)));

        RouteCondition lambda = custom(request -> true);
        RouteCondition regexHeader = header("X-R", regex("\\d+"));
        RouteCondition remote = remoteAddress("192.168.0.0/16");
        assertEquals(List.of(method(HttpMethod.GET), peer, regexHeader, remote, lambda),
            RouteConditions.conjuncts(RouteConditions.normalize(all(lambda, peer, regexHeader, remote, method(HttpMethod.GET)))),
            "the tier of the regular expressions and the client address, stably, before the lambdas");
    }

    @Test
    void aPeerAddressSelectsAHandlerRoute() {
        RouteAssembly assembly = new RouteAssembly(null, ConversionService.SHARED, uri -> uri, route -> { });
        DefaultHttpRouteBuilder routes = new DefaultHttpRouteBuilder(assembly);
        routes.GET("/admin")
            .where(peerAddress("127.0.0.0/8", "::1"))
            .handle((request, pathVariables) -> HttpResponse.ok("admin"));
        assembly.addImplicitHeadRoutes();
        Router router = new DefaultRouter(List.of(), List.of(() -> assembly));

        assertNotNull(router.findClosest(from("127.0.0.1")));
        assertNull(router.findClosest(new RemoteRequest(HttpRequest.GET("/admin").header("X-Forwarded-For", "127.0.0.1"),
            new InetSocketAddress("203.0.113.9", 50000))), "a forwarded loopback address from another peer");
    }

    private static HttpRequest<?> from(String address) {
        return new RemoteRequest(HttpRequest.GET("/admin"), new InetSocketAddress(address, 50000));
    }

    /**
     * A request from a peer address.
     */
    private static final class RemoteRequest extends HttpRequestWrapper<Object> {
        private final @Nullable InetSocketAddress remote;

        @SuppressWarnings("unchecked")
        RemoteRequest(HttpRequest<?> delegate, @Nullable InetSocketAddress remote) {
            super((HttpRequest<Object>) delegate);
            this.remote = remote;
        }

        @Override
        public @Nullable InetSocketAddress getRemoteAddress() {
            return remote;
        }
    }

    private static boolean meets(io.micronaut.web.router.builder.RouteCondition condition, io.micronaut.http.HttpRequest<?> request) {
        return io.micronaut.web.router.builder.RouteConditions.matches(condition, request, RouteConditionContext.fallback());
    }
}
