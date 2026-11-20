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
package io.micronaut.http.gateway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.exceptions.BeanInstantiationException;
import io.micronaut.core.io.socket.SocketUtils;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.builder.ValueMatcher;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing: the predicates of gateway routes, their order, and how they live with the other
 * routes of the application.
 */
class RoutingTest extends GatewayTestBase {

    static final String SPEC = "RoutingTest";
    private final int managementPort = SocketUtils.findAvailableTcpPort();

    @Override
    String specName() {
        return SPEC;
    }

    @Override
    Map<String, Object> gatewayProperties() {
        Map<String, Object> p = new HashMap<>();
        String r = "micronaut.gateway.routes.";
        p.put(r + "host.uri", "${test.upstream}");
        p.put(r + "host.predicates", List.of("Path=/rt/host/**", "Host=**.example.org,{sub}.myhost.org"));
        p.put(r + "host.filters", List.of("SetPath=/inspect/host"));
        p.put(r + "methods.uri", "${test.upstream}");
        p.put(r + "methods.predicates", List.of("Path=/rt/methods/**", "Method=GET,POST"));
        p.put(r + "methods.filters", List.of("SetPath=/inspect/methods"));
        p.put(r + "header.uri", "${test.upstream}");
        p.put(r + "header.predicates", List.of("Path=/rt/header/**", "Header=X-Version, v\\d+"));
        p.put(r + "header.filters", List.of("SetPath=/inspect/header"));
        p.put(r + "header-present.uri", "${test.upstream}");
        p.put(r + "header-present.predicates", List.of("Path=/rt/header-present/**", "Header=X-Present"));
        p.put(r + "header-present.filters", List.of("SetPath=/inspect/header-present"));
        p.put(r + "query.uri", "${test.upstream}");
        p.put(r + "query.predicates", List.of("Path=/rt/query/**", "Query=color, gr(e|a)y"));
        p.put(r + "query.filters", List.of("SetPath=/inspect/query"));
        p.put(r + "query-present.uri", "${test.upstream}");
        p.put(r + "query-present.predicates", List.of("Path=/rt/query-present/**", "Query=debug"));
        p.put(r + "query-present.filters", List.of("SetPath=/inspect/query-present"));
        p.put(r + "cookie.uri", "${test.upstream}");
        p.put(r + "cookie.predicates", List.of("Path=/rt/cookie/**", "Cookie=session, ab.+"));
        p.put(r + "cookie.filters", List.of("SetPath=/inspect/cookie"));
        p.put(r + "peer.uri", "${test.upstream}");
        p.put(r + "peer.predicates", List.of("Path=/rt/peer/**", "RemoteAddr=127.0.0.1/32,::1"));
        p.put(r + "peer.filters", List.of("SetPath=/inspect/peer"));
        p.put(r + "peer-other.uri", "${test.upstream}");
        p.put(r + "peer-other.predicates", List.of("Path=/rt/peer-other/**", "RemoteAddr=10.0.0.0/8"));
        p.put(r + "xff.uri", "${test.upstream}");
        p.put(r + "xff.predicates", List.of("Path=/rt/xff/**", "XForwardedRemoteAddr=192.168.0.0/16"));
        p.put(r + "xff.filters", List.of("SetPath=/inspect/xff"));
        p.put(r + "time.uri", "${test.upstream}");
        p.put(r + "time.predicates", List.of("Path=/rt/time/**", "Between=2020-01-01T00:00:00Z, 2100-01-01T00:00:00Z"));
        p.put(r + "time.filters", List.of("SetPath=/inspect/time"));
        p.put(r + "time-past.uri", "${test.upstream}");
        p.put(r + "time-past.predicates", List.of("Path=/rt/time-past/**", "Before=" + Instant.parse("2020-01-01T00:00:00Z").toEpochMilli()));
        p.put(r + "segment.uri", "${test.upstream}");
        p.put(r + "segment.predicates", List.of("Path=/rt/seg/{segment}", "Path=/rt/other/{segment}"));
        p.put(r + "segment.filters", List.of("SetPath=/inspect/{segment}"));
        p.put(r + "wildcard.uri", "${test.upstream}");
        p.put(r + "wildcard.predicates", List.of("Path=/rt/wild/*/end"));
        p.put(r + "wildcard.filters", List.of("SetPath=/inspect/wild"));
        p.put(r + "exact.uri", "${test.upstream}");
        p.put(r + "exact.predicates", List.of("Path=/rt/exact"));
        p.put(r + "exact.filters", List.of("PrefixPath=/inspect"));
        p.put(r + "regex.uri", "${test.upstream}");
        p.put(r + "regex.predicates", List.of("Path=/rt/num/{id:[0-9]+}"));
        p.put(r + "regex.filters", List.of("SetPath=/inspect/num/{id}"));
        p.put(r + "missing-service.uri", "lb://no-such-service");
        p.put(r + "missing-service.predicates", List.of("Path=/rt/missing-service/**"));
        p.put(r + "scheme.uri", "HTTP://127.0.0.1:${test.upstream-port}");
        p.put(r + "scheme.predicates", List.of("Path=/rt/scheme/**"));
        p.put(r + "scheme.filters", List.of("SetPath=/inspect/scheme"));
        // a catch-all route without predicates: it matches every other path, but not the management port
        p.put(r + "catch-all.uri", "${test.upstream}");
        p.put(r + "catch-all.order", 100);
        p.put(r + "catch-all.filters", List.of("PrefixPath=/inspect/catch-all"));
        p.put("micronaut.gateway.forwarded.trusted-proxies", List.of("127.0.0.1/32", "::1", "10.0.0.0/8"));
        p.put("endpoints.all.port", managementPort);
        p.put("endpoints.health.enabled", true);
        p.put("endpoints.health.sensitive", false);
        p.put("test.upstream-port", upstream.getPort());
        return p;
    }

    @Test
    void rt01PathVariablesAreUsedByLaterFiltersAndSeveralPathsRoute() throws IOException {
        assertEquals("/inspect/abc", get("/rt/seg/abc").inspected("path"));
        assertEquals("/inspect/xyz", get("/rt/other/xyz").inspected("path"));
        assertEquals("/inspect/wild", get("/rt/wild/anything/end").inspected("path"));
        // * is one segment: the catch-all route answers
        assertEquals("/inspect/catch-all/rt/wild/a/b/end", get("/rt/wild/a/b/end").inspected("path"));
    }

    @Test
    void rt02ATrailingSlashMatchesTheRouteWithoutOneAndIsRelayedAsSent() throws IOException {
        // the router ignores a trailing slash, for controllers and gateway routes alike
        assertEquals("/inspect/rt/exact", get("/rt/exact").inspected("path"));
        assertEquals("/inspect/rt/exact/", get("/rt/exact/").inspected("path"));
    }

    @Test
    void rt03PercentEscapesAndRegexSegmentsMatchConsistently() throws IOException {
        assertEquals("/inspect/num/42", get("/rt/num/42").inspected("path"));
        assertEquals("/inspect/catch-all/rt/num/4a", get("/rt/num/4a").inspected("path"));
        // the raw path is matched and relayed: an escape is kept as sent
        assertEquals("/inspect/catch-all/rt/a%20b%C3%A9", get("/rt/a%20b%C3%A9").inspected("path"));
        // a variable is decoded once, and encoded once by SetPath
        assertEquals("/inspect/a%20b", get("/rt/seg/a%20b").inspected("path"));
    }

    @Test
    void rt04HostPatternsMatchWithAndWithoutAPort() throws IOException {
        assertEquals("/inspect/host", sendWithHost("/rt/host/x", "www.example.org").inspected("path"));
        assertEquals("/inspect/host", sendWithHost("/rt/host/x", "a.b.example.org").inspected("path"));
        assertEquals("/inspect/host", sendWithHost("/rt/host/x", "api.myhost.org:8443").inspected("path"));
        assertEquals("/inspect/host", sendWithHost("/rt/host/x", "WWW.EXAMPLE.ORG").inspected("path"));
        // not a match: the catch-all route answers
        assertEquals("/inspect/catch-all/rt/host/x", sendWithHost("/rt/host/x", "example.com").inspected("path"));
        assertEquals("/inspect/catch-all/rt/host/x", sendWithHost("/rt/host/x", "a.b.myhost.org").inspected("path"));
    }

    @Test
    void rt05ARequestWithoutAHostDoesNotBreakTheHostPredicate() throws IOException {
        // HTTP/1.1 requires a Host: the server rejects the request, it does not fail
        RawHttp.Response noHost = RawHttp.exchange(gateway.getPort(), "GET /rt/host/x HTTP/1.1\r\nConnection: close\r\n\r\n");
        assertTrue(noHost.status() == 400 || noHost.status() == 200 && noHost.inspected("path").startsWith("/inspect/catch-all"), noHost.toString());
        // HTTP/1.0 has no Host: the host predicate does not match
        RawHttp.Response http10 = RawHttp.exchange(gateway.getPort(), "GET /rt/host/x HTTP/1.0\r\n\r\n");
        assertEquals(200, http10.status(), http10.toString());
        assertEquals("/inspect/catch-all/rt/host/x", http10.inspected("path"));
    }

    @Test
    void rt06ATrailingDotOfTheHostMatches() throws IOException {
        assertEquals("/inspect/host", sendWithHost("/rt/host/x", "www.example.org.").inspected("path"));
    }

    @Test
    void rt07AMethodThatDoesNotMatchIsNoMatchNot405() throws IOException {
        assertEquals("/inspect/methods", get("/rt/methods/x").inspected("path"));
        assertEquals("/inspect/methods", send("POST", "/rt/methods/x", "b").inspected("path"));
        // PUT: the route does not exist for it, the catch-all route answers
        assertEquals("/inspect/catch-all/rt/methods/x", send("PUT", "/rt/methods/x", "b").inspected("path"));
    }

    @Test
    void rt08HeaderPresenceRegexMissingAndCommaSeparatedValues() throws IOException {
        assertEquals("/inspect/header", get("/rt/header/x", "X-Version: v12").inspected("path"));
        assertEquals("/inspect/catch-all/rt/header/x", get("/rt/header/x", "X-Version: beta").inspected("path"));
        assertEquals("/inspect/catch-all/rt/header/x", get("/rt/header/x").inspected("path"));
        assertEquals("/inspect/header", get("/rt/header/x", "X-Version: beta, v2").inspected("path"));
        assertEquals("/inspect/header-present", get("/rt/header-present/x", "X-Present: anything").inspected("path"));
        assertEquals("/inspect/catch-all/rt/header-present/x", get("/rt/header-present/x").inspected("path"));
    }

    @Test
    void rt09QueryPresentRegexAndEmptyValue() throws IOException {
        assertEquals("/inspect/query", get("/rt/query/x?color=gray").inspected("path"));
        assertEquals("/inspect/query", get("/rt/query/x?color=green&color=grey").inspected("path"));
        assertEquals("/inspect/catch-all/rt/query/x", get("/rt/query/x?color=blue").inspected("path"));
        assertEquals("/inspect/query-present", get("/rt/query-present/x?debug=").inspected("path"));
        assertEquals("/inspect/query-present", get("/rt/query-present/x?debug").inspected("path"));
        assertEquals("/inspect/catch-all/rt/query-present/x", get("/rt/query-present/x").inspected("path"));
    }

    @Test
    void rt10CookieRegex() throws IOException {
        assertEquals("/inspect/cookie", get("/rt/cookie/x", "Cookie: a=1; session=abc").inspected("path"));
        assertEquals("/inspect/catch-all/rt/cookie/x", get("/rt/cookie/x", "Cookie: session=xyz").inspected("path"));
        assertEquals("/inspect/catch-all/rt/cookie/x", get("/rt/cookie/x").inspected("path"));
    }

    @Test
    void rt11RemoteAddrUsesThePeerAndXForwardedRemoteAddrTheRightMostUntrustedHop() throws IOException {
        // the peer is 127.0.0.1: a spoofed X-Forwarded-For does not change it
        assertEquals("/inspect/peer", get("/rt/peer/x", "X-Forwarded-For: 10.9.9.9").inspected("path"));
        assertEquals("/inspect/catch-all/rt/peer-other/x", get("/rt/peer-other/x", "X-Forwarded-For: 10.9.9.9").inspected("path"));
        // the peer is a trusted proxy: walking from the right past the trusted 10.0.0.0/8 hop
        assertEquals("/inspect/xff", get("/rt/xff/x", "X-Forwarded-For: 172.16.0.1, 192.168.4.4, 10.1.1.1").inspected("path"));
        // the right-most untrusted hop is not in the range, even when a hop left of it is
        assertEquals("/inspect/catch-all/rt/xff/x", get("/rt/xff/x", "X-Forwarded-For: 192.168.4.4, 172.16.0.1").inspected("path"));
        // several header lines are one list, in order
        assertEquals("/inspect/xff", get("/rt/xff/x", "X-Forwarded-For: 172.16.0.1", "X-Forwarded-For: 192.168.4.4").inspected("path"));
    }

    @Test
    void rt12TimePredicates() throws IOException {
        assertEquals("/inspect/time", get("/rt/time/x").inspected("path"));
        assertEquals("/inspect/catch-all/rt/time-past/x", get("/rt/time-past/x").inspected("path"));
    }

    @Test
    void rt14ConditionsCombineWithAndOrAndNot() throws IOException {
        assertEquals("/inspect/combined", get("/rt/combined/x", "X-A: 1").inspected("path"));
        assertEquals("/inspect/combined", get("/rt/combined/x?b=1").inspected("path"));
        assertEquals("/inspect/catch-all/rt/combined/x", get("/rt/combined/x", "X-A: 1", "X-Blocked: yes").inspected("path"));
        assertEquals("/inspect/catch-all/rt/combined/x", get("/rt/combined/x").inspected("path"));
    }

    @Test
    void rt15TheLowestOrderWinsAmongEquallySpecificRoutes() throws IOException {
        assertEquals("/inspect/first", get("/rt/ordered/x").inspected("path"));
    }

    @Test
    void rt17ARouteToAServiceWithoutInstancesIs503() throws IOException {
        assertEquals(503, get("/rt/missing-service/x").status());
    }

    @Test
    void rt18AndRt23TheManagementPortNeverReachesGatewayRoutes() throws IOException {
        RawHttp.Response management = RawHttp.exchange(managementPort, "GET /rt/anything HTTP/1.1\r\nHost: localhost:" + gateway.getPort() + "\r\nConnection: close\r\n\r\n");
        assertEquals(404, management.status(), management.toString());
        RawHttp.Response health = RawHttp.exchange(managementPort, "GET /health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n");
        assertEquals(200, health.status(), health.toString());
        // the catch-all route answers on the application port
        assertEquals("/inspect/catch-all/rt/anything", get("/rt/anything").inspected("path"));
    }

    @Test
    void rt19GatewayRoutesLiveWithControllers() throws IOException {
        RawHttp.Response local = get("/local/hello");
        assertEquals(200, local.status());
        assertEquals("local", local.text());
        assertEquals("/inspect/catch-all/local/other", get("/local/other").inspected("path"));
    }

    @Test
    void rt20TheConditionsAndFiltersOfAGroupApplyToAProxyHandlerRoute() throws IOException {
        RawHttp.Response grouped = get("/grp/x", "X-Group: yes");
        assertEquals("/inspect/grp/x", grouped.inspected("path"));
        assertEquals("applied", grouped.header("X-Group-Filter"));
        assertEquals("/inspect/catch-all/grp/x", get("/grp/x").inspected("path"));
    }

    @Test
    void rt24TheSchemeOfTheUriIsCaseInsensitive() throws IOException {
        assertEquals("/inspect/scheme", get("/rt/scheme/x").inspected("path"));
    }

    @Test
    void rt25AUriWithoutASchemeIsRejectedByTheConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> GatewayRoute.builder("x").uri("localhost:8080").build());
        assertThrows(IllegalArgumentException.class, () -> GatewayRoute.builder("x").uri("/relative").build());
        assertThrows(IllegalArgumentException.class, () -> GatewayRoute.builder("x").uri("ftp://host").build());
        Map<String, Object> properties = Map.of(
            "spec.name", SPEC + "-invalid",
            "micronaut.server.port", -1,
            "micronaut.gateway.routes.bad.uri", "localhost",
            "micronaut.gateway.routes.bad.predicates", List.of("Path=/bad/**"));
        Exception failure = assertThrows(Exception.class, () -> ApplicationContext.run(EmbeddedServer.class, properties).close());
        assertTrue(rootMessage(failure).contains("bad"), rootMessage(failure));
    }

    static String rootMessage(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        for (Throwable t = failure; t != null; t = t.getCause()) {
            messages.append(t.getMessage()).append(" | ");
        }
        return messages.toString();
    }

    private RawHttp.Response sendWithHost(String path, String host) throws IOException {
        return RawHttp.exchange(gateway.getPort(), "GET " + path + " HTTP/1.1\r\nHost: " + host + "\r\nConnection: close\r\n\r\n");
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class Routes implements GatewayRoutes {
        private final String upstream;

        Routes(@Value("${test.upstream}") String upstream) {
            this.upstream = upstream;
        }

        @Override
        public void routes(GatewayRouteBuilder gateway) {
            gateway.route("combined")
                .path("/rt/combined/**")
                .where(RequestCondition.any(RequestCondition.header("X-A"), RequestCondition.query("b")),
                    RequestCondition.not(RequestCondition.header("X-Blocked")))
                .filters(GatewayFilters.setPath("/inspect/combined"))
                .uri(upstream);
            gateway.route("second").path("/rt/ordered/**").order(2).filters(GatewayFilters.setPath("/inspect/second")).uri(upstream);
            gateway.route("first").path("/rt/ordered/**").order(1).filters(GatewayFilters.setPath("/inspect/first")).uri(upstream);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = SPEC)
    static class GroupRoutes implements HttpRoutes {
        private final ProxyHandlerFactory proxies;
        private final String upstream;

        GroupRoutes(ProxyHandlerFactory proxies, @Value("${test.upstream}") String upstream) {
            this.proxies = proxies;
            this.upstream = upstream;
        }

        @Override
        public void routes(HttpRouteBuilder routes) {
            routes.path("/grp", group -> {
                group.where(RequestCondition.header("X-Group", ValueMatcher.equalTo("yes")));
                group.after((request, response) -> response.header("X-Group-Filter", "applied"));
                TestProxyRoutes.proxy(group, "/", proxies.create(GatewayRoute.builder("grouped").uri(upstream).filters(GatewayFilters.prefixPath("/inspect")).build()));
            });
        }
    }

    @Controller("/local")
    @Requires(property = "spec.name", value = SPEC)
    static class Local {
        @Get("/hello")
        String hello() {
            return "local";
        }
    }

    @Test
    void anInvalidPredicateFailsTheStartup() {
        Map<String, Object> properties = Map.of(
            "spec.name", SPEC + "-invalid",
            "micronaut.server.port", -1,
            "micronaut.gateway.routes.bad.uri", "http://localhost:1",
            "micronaut.gateway.routes.bad.predicates", List.of("Nope=/bad/**"));
        Exception failure = assertThrows(Exception.class, () -> ApplicationContext.run(EmbeddedServer.class, properties).close());
        assertTrue(rootMessage(failure).contains("Unknown predicate Nope of the gateway route bad"), rootMessage(failure));
        assertTrue(failure instanceof RuntimeException || failure instanceof BeanInstantiationException);
    }
}
