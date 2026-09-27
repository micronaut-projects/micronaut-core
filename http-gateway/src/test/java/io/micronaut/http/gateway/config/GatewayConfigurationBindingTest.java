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
package io.micronaut.http.gateway.config;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.gateway.GatewayCircuitBreaker;
import io.micronaut.http.gateway.GatewayConfiguration;
import io.micronaut.http.gateway.GatewayRetry;
import io.micronaut.http.gateway.GatewayRoute;
import io.micronaut.web.router.builder.Cidr;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Binding gateway routes from the configuration: the shortcut and the name and args syntax, the
 * built-in factories, and the errors.
 */
class GatewayConfigurationBindingTest {

    private static GatewayRoute bind(Map<String, Object> properties) {
        Map<String, Object> all = new LinkedHashMap<>(properties);
        try (ApplicationContext context = ApplicationContext.run(all)) {
            ConfiguredGatewayRoutes routes = context.getBean(ConfiguredGatewayRoutes.class);
            GatewayRouteConfiguration configuration = context.getBean(GatewayRouteConfiguration.class);
            return routes.toRoute(configuration);
        }
    }

    @Test
    void theReplayLimitOfTheRetriesBindsAsReadableBytes() {
        GatewayRoute route = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters[0].name", "Retry",
            "micronaut.gateway.routes.r.filters[0].args.retries", "2",
            "micronaut.gateway.routes.r.filters[0].args.max-replay-bytes", "64KB"
        ));
        assertEquals(64 * 1024, route.getRetry().maxReplayBytes());
        GatewayRoute defaults = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters", List.of("Retry=2")
        ));
        assertEquals(GatewayRetry.DEFAULT_MAX_REPLAY_BYTES, defaults.getRetry().maxReplayBytes());
    }

    @Test
    void theSizeFiltersBindTheirLimits() {
        GatewayRoute route = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters", List.of("RequestSize=5KB", "RequestHeaderSize=1KB, 20")
        ));
        assertEquals(List.of("RequestSize=5120", "RequestHeaderSize=1024, 20"), route.getFilters().stream().map(Object::toString).toList());
        GatewayRoute defaults = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters[0].name", "RequestSize",
            "micronaut.gateway.routes.r.filters[1].name", "RequestHeaderSize"
        ));
        assertEquals(List.of("RequestSize=5242880", "RequestHeaderSize=16000, 0"), defaults.getFilters().stream().map(Object::toString).toList());
    }

    @Test
    void theShortcutAndTheNameAndArgsSyntaxBindTheSameRoute() {
        GatewayRoute shortcut = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "lb://orders",
            "micronaut.gateway.routes.r.order", 7,
            "micronaut.gateway.routes.r.response-timeout", "2s",
            "micronaut.gateway.routes.r.http-version", "HTTP_2_0",
            "micronaut.gateway.routes.r.predicates", List.of("Path=/a/**, /b/{id}", "Method=GET,POST", "Header=X-T, \\d{1,3}"),
            "micronaut.gateway.routes.r.filters", List.of("StripPrefix=1", "Retry=2", "CircuitBreaker=orders")
        ));
        assertEquals("r", shortcut.getId());
        assertEquals("lb://orders", shortcut.getUri().toString());
        assertTrue(shortcut.isLoadBalanced());
        assertEquals("orders", shortcut.getServiceId());
        assertEquals(7, shortcut.getOrder());
        assertEquals(Duration.ofSeconds(2), shortcut.getResponseTimeout());
        assertEquals(List.of("/a/**", "/b/{id}"), shortcut.getPaths());
        assertEquals(2, shortcut.getConditions().size());
        assertEquals(List.of("StripPrefix=1"), shortcut.getFilters().stream().map(Object::toString).toList());
        GatewayRetry retry = shortcut.getRetry();
        assertEquals(2, retry.retries());
        assertEquals(Set.of("GET", "HEAD"), retry.methods());
        assertEquals(Set.of(502, 503, 504), retry.statuses());
        assertEquals(GatewayCircuitBreaker.of("orders"), shortcut.getCircuitBreaker());

        GatewayRoute named = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "lb://orders",
            "micronaut.gateway.routes.r.predicates[0].name", "Path",
            "micronaut.gateway.routes.r.predicates[0].args.patterns", "/a/**",
            "micronaut.gateway.routes.r.filters[0].name", "Retry",
            "micronaut.gateway.routes.r.filters[0].args.retries", "4",
            "micronaut.gateway.routes.r.filters[0].args.methods", "GET, PUT",
            "micronaut.gateway.routes.r.filters[0].args.statuses", "SERVICE_UNAVAILABLE, 500",
            "micronaut.gateway.routes.r.filters[0].args.delay", "10ms"
        ));
        assertEquals(List.of("/a/**"), named.getPaths());
        GatewayRetry bound = named.getRetry();
        assertEquals(4, bound.retries());
        assertEquals(Set.of("GET", "PUT"), bound.methods());
        assertEquals(Set.of(503, 500), bound.statuses());
        assertEquals(Duration.ofMillis(10), bound.delay());

        GatewayRoute breaker = bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters[0].name", "CircuitBreaker",
            "micronaut.gateway.routes.r.filters[0].args.name", "orders",
            "micronaut.gateway.routes.r.filters[0].args.failure-statuses", "SERVICE_UNAVAILABLE, 504",
            "micronaut.gateway.routes.r.filters[0].args.fallback-statuses", "503",
            "micronaut.gateway.routes.r.filters[0].args.fallback", "ordersFallback",
            "test.fallback", true
        ));
        assertEquals(Set.of(503, 504), breaker.getCircuitBreaker().failureStatuses());
        assertEquals(Set.of(503), breaker.getCircuitBreaker().fallbackStatuses());
        assertTrue(breaker.getCircuitBreaker().isFailure(504));
        assertFalse(breaker.getCircuitBreaker().isFailure(500));
    }

    @jakarta.inject.Singleton
    @jakarta.inject.Named("ordersFallback")
    @io.micronaut.context.annotation.Requires(property = "test.fallback")
    static class OrdersFallback implements io.micronaut.web.router.builder.AsyncRequestHandler {
        @Override
        public java.util.concurrent.CompletionStage<? extends io.micronaut.http.HttpResponse<?>> handle(HttpRequest<?> request, io.micronaut.http.PathVariables pathVariables) {
            return java.util.concurrent.CompletableFuture.completedFuture(io.micronaut.http.HttpResponse.ok());
        }
    }

    @Test
    void dr04AnUnknownNameOrAnInvalidArgumentIsAClearError() {
        ConfigurationException unknownFilter = assertThrows(ConfigurationException.class, () -> bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters", List.of("NoSuchFilter=1"))));
        assertEquals("Unknown filter NoSuchFilter of the gateway route r", unknownFilter.getMessage());
        ConfigurationException badArgument = assertThrows(ConfigurationException.class, () -> bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters", List.of("StripPrefix=abc"))));
        assertTrue(badArgument.getMessage().contains("StripPrefix"), badArgument.getMessage());
        ConfigurationException unknownArgument = assertThrows(ConfigurationException.class, () -> bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.filters[0].name", "Retry",
            "micronaut.gateway.routes.r.filters[0].args.retires", "2")));
        assertTrue(unknownArgument.getMessage().contains("Unknown argument retires"), unknownArgument.getMessage());
        ConfigurationException noUri = assertThrows(ConfigurationException.class, () -> bind(Map.of(
            "micronaut.gateway.routes.r.predicates", List.of("Path=/x/**"))));
        assertTrue(noUri.getMessage().contains("has no uri"), noUri.getMessage());
        ConfigurationException badPath = assertThrows(ConfigurationException.class, () -> bind(Map.of(
            "micronaut.gateway.routes.r.uri", "http://x",
            "micronaut.gateway.routes.r.predicates", List.of("Before=yesterday"))));
        assertTrue(badPath.getMessage().contains("yesterday"), badPath.getMessage());
    }

    @Test
    void shortcutArgumentsAndTheLastFieldTakingTheRest() {
        GatewayArguments arguments = GatewayArguments.ofShortcut("x", List.of("name", "regexp"), "X-Tenant, a{1,3}, b", ConversionService.SHARED);
        assertEquals("X-Tenant", arguments.get("name"));
        assertEquals("a{1,3}, b", arguments.get("regexp"));
        GatewayArguments list = GatewayArguments.ofShortcut("x", List.of("*methods"), "GET, POST", ConversionService.SHARED);
        assertEquals(List.of("GET", "POST"), list.getList("methods"));
        assertThrows(ConfigurationException.class, () -> GatewayArguments.ofShortcut("x", List.of(), "1", ConversionService.SHARED));
    }

    @Test
    void hostPatterns() {
        assertTrue(BuiltInGatewayFactories.hostMatcher("**.example.org").toString().length() > 0);
        var any = io.micronaut.web.router.builder.RequestCondition.host(BuiltInGatewayFactories.hostMatcher("**.example.org"));
        assertTrue(io.micronaut.web.router.builder.RequestConditions.matches(any, HttpRequest.GET("/").header("Host", "a.b.example.org"), io.micronaut.web.router.RouteConditionContext.fallback()));
        assertFalse(io.micronaut.web.router.builder.RequestConditions.matches(any, HttpRequest.GET("/").header("Host", "example.org"), io.micronaut.web.router.RouteConditionContext.fallback()));
        var one = io.micronaut.web.router.builder.RequestCondition.host(BuiltInGatewayFactories.hostMatcher("{sub}.myhost.org:8080"));
        assertTrue(io.micronaut.web.router.builder.RequestConditions.matches(one, HttpRequest.GET("/").header("Host", "api.myhost.org"), io.micronaut.web.router.RouteConditionContext.fallback()));
        assertFalse(io.micronaut.web.router.builder.RequestConditions.matches(one, HttpRequest.GET("/").header("Host", "a.b.myhost.org"), io.micronaut.web.router.RouteConditionContext.fallback()));
    }

    @Test
    void listElementsOfAHeader() {
        var matcher = io.micronaut.web.router.builder.RequestCondition.header("X", BuiltInGatewayFactories.listElement("b"));
        assertTrue(io.micronaut.web.router.builder.RequestConditions.matches(matcher, HttpRequest.GET("/").header("X", "a, b"), io.micronaut.web.router.RouteConditionContext.fallback()));
        assertTrue(io.micronaut.web.router.builder.RequestConditions.matches(matcher, HttpRequest.GET("/").header("X", "b"), io.micronaut.web.router.RouteConditionContext.fallback()));
        assertFalse(io.micronaut.web.router.builder.RequestConditions.matches(matcher, HttpRequest.GET("/").header("X", "ab, c"), io.micronaut.web.router.RouteConditionContext.fallback()));
    }

    @Test
    void fw06AndFw13TheClientAddressIsTheRightMostUntrustedHop() {
        List<Cidr> trusted = List.of(Cidr.parse("10.0.0.0/8"), Cidr.parse("127.0.0.1"));
        HttpRequest<?> request = new io.micronaut.http.HttpRequestWrapper<>(HttpRequest.GET("/").header("X-Forwarded-For", "1.1.1.1, 2.2.2.2, 10.0.0.5")) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("127.0.0.1", 5000);
            }
        };
        assertArrayEquals(new byte[]{2, 2, 2, 2}, BuiltInGatewayFactories.clientAddress(request, trusted));
        HttpRequest<?> untrustedPeer = new io.micronaut.http.HttpRequestWrapper<>(HttpRequest.GET("/").header("X-Forwarded-For", "1.1.1.1")) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("192.0.2.1", 5000);
            }
        };
        assertArrayEquals(new byte[]{(byte) 192, 0, 2, 1}, BuiltInGatewayFactories.clientAddress(untrustedPeer, trusted));
        HttpRequest<?> garbage = new io.micronaut.http.HttpRequestWrapper<>(HttpRequest.GET("/").header("X-Forwarded-For", "not-an-address")) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress("127.0.0.1", 5000);
            }
        };
        assertNull(BuiltInGatewayFactories.clientAddress(garbage, trusted));
        // canonical forms: a mapped IPv4 address is the IPv4 address, no leading zeros alias
        GatewayConfiguration.ForwardedConfiguration forwarded = new GatewayConfiguration.ForwardedConfiguration();
        forwarded.setTrustedProxies(List.of("10.0.0.0/8"));
        var headers = forwarded.toForwardedHeaders();
        assertTrue(trustedBy(headers, "::ffff:10.1.2.3"));
        assertFalse(trustedBy(headers, "11.0.0.1"));
        assertNull(Cidr.address("010.000.000.001x"));
    }

    private static boolean trustedBy(io.micronaut.http.server.util.ForwardedHeaders headers, String address) {
        HttpRequest<?> inbound = new io.micronaut.http.HttpRequestWrapper<>(HttpRequest.GET("/").header("X-Forwarded-For", "203.0.113.9").header("Host", "gw")) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return new InetSocketAddress(address, 5000);
            }
        };
        var outbound = HttpRequest.GET("/");
        headers.write(inbound, outbound);
        return outbound.getHeaders().get("X-Forwarded-For").startsWith("203.0.113.9");
    }
}
