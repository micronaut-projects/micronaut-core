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

import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.bind.MatchedPathVariables;
import io.micronaut.http.exceptions.HttpStatusException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proxy transforms on an upstream request and response, without a server.
 */
class GatewayFiltersTest {

    private static ProxyExchange exchange(String inboundUri, Map<String, Object> variables, String... patterns) {
        HttpRequest<?> inbound = HttpRequest.GET(inboundUri).header("Host", "gw.example:8443");
        List<GatewayPathPattern> parsed = java.util.Arrays.stream(patterns).map(GatewayPathPattern::parse).toList();
        return new ProxyExchange(inbound, new MatchedPathVariables(variables, ConversionService.SHARED), "route", URI.create("http://up:8080"), true, parsed);
    }

    private static MutableHttpRequest<Object> upstream(String uri) {
        return HttpRequest.GET(uri);
    }

    private static String apply(GatewayFilter filter, String uri) throws Exception {
        MutableHttpRequest<Object> request = upstream(uri);
        filter.filterRequest(exchange(uri, Map.of()), request);
        URI result = request.getUri();
        return result.getRawPath() + (result.getRawQuery() == null ? "" : "?" + result.getRawQuery());
    }

    @Test
    void theDescriptionOfAFilterMasksCredentials() {
        assertEquals("AddRequestHeader=Authorization, ******", GatewayFilters.addRequestHeader("Authorization", "Bearer abc").toString());
        assertEquals("SetRequestHeader=X-Api-Key, ******", GatewayFilters.setRequestHeader("X-Api-Key", "k").toString());
        assertEquals("AddRequestParameter=access_token, ******", GatewayFilters.addRequestParameter("access_token", "t").toString());
        assertEquals("SetResponseHeader=Set-Cookie, ******", GatewayFilters.setResponseHeader("Set-Cookie", "s=1").toString());
        assertEquals("AddRequestHeader=X-Gw, mn", GatewayFilters.addRequestHeader("X-Gw", "mn").toString());
    }

    @Test
    void aRouteRelaysOnlyWebSocketUpgradesByDefault() {
        assertEquals(java.util.Set.of("websocket"), GatewayRoute.builder("r").uri("http://up").build().getUpgradeProtocols());
        assertEquals(java.util.Set.of("websocket", "tcp"),
            GatewayRoute.builder("r").uri("http://up").upgradeProtocols("WebSocket", "tcp").build().toBuilder().build().getUpgradeProtocols());
        assertTrue(GatewayRoute.builder("r").uri("http://up").upgradeProtocols().build().getUpgradeProtocols().isEmpty());
    }

    @Test
    void aTrailingSlashIsKeptOnce() {
        assertEquals(List.of("/health/"), GatewayRoute.builder("r").uri("http://up").path("/health/").build().getRouteTemplates());
    }

    @Test
    void theTemplatesOfARoute() {
        assertEquals(List.of("/api/orders", "/api/orders/{+path}", "/health"),
            GatewayRoute.builder("r").uri("http://up").path("/api/orders/**", "/health").build().getRouteTemplates());
    }

    @Test
    void aRouteThatRewritesResponseBodiesAsksForTheCodingsItDecodes() {
        MutableHttpRequest<Object> request = upstream("/x");
        request.getHeaders().set("Accept-Encoding", "br, gzip;q=0.8, zstd, deflate");
        DefaultProxyHandler.narrowAcceptEncoding(request.getHeaders());
        assertEquals("gzip;q=0.8, deflate", request.getHeaders().get("Accept-Encoding"));
        request.getHeaders().set("Accept-Encoding", "br");
        DefaultProxyHandler.narrowAcceptEncoding(request.getHeaders());
        assertEquals("identity", request.getHeaders().get("Accept-Encoding"));
    }

    @Test
    void stripPrefix() throws Exception {
        assertEquals("/c/d?q=%2B", apply(GatewayFilters.stripPrefix(2), "/a/b/c/d?q=%2B"));
        assertEquals("/", apply(GatewayFilters.stripPrefix(5), "/a/b"));
        assertEquals("/", apply(GatewayFilters.stripPrefix(1), "/a"));
        assertEquals("/b/", apply(GatewayFilters.stripPrefix(1), "/a/b/"));
        assertEquals("/b%20c", apply(GatewayFilters.stripPrefix(1), "/a%2Fx/b%20c"));
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.stripPrefix(0));
    }

    @Test
    void stripPrefixSendsTheRemovedPrefix() throws Exception {
        MutableHttpRequest<Object> request = upstream("/api/v1/items");
        GatewayFilters.stripPrefix(2).filterRequest(exchange("/api/v1/items", Map.of()), request);
        assertEquals("/api/v1", request.getHeaders().get("X-Forwarded-Prefix"));
        request = upstream("/api/v1/items");
        request.header("X-Forwarded-Prefix", "/edge");
        GatewayFilters.stripPrefix(1).filterRequest(exchange("/api/v1/items", Map.of()), request);
        assertEquals("/edge/api", request.getHeaders().get("X-Forwarded-Prefix"));
    }

    @Test
    void prefixPathAndSetPathUseTheRawValuesOfTheVariables() throws Exception {
        assertEquals("/v2/items", apply(GatewayFilters.prefixPath("/v2"), "/items"));
        assertEquals("/v2/", apply(GatewayFilters.prefixPath("/v2/"), "/"));
        MutableHttpRequest<Object> request = upstream("/sp/a+b%2Fc");
        GatewayFilters.setPath("/v2/{segment}/x").filterRequest(exchange("/sp/a+b%2Fc", Map.of("segment", "a+b/c"), "/sp/{segment}"), request);
        assertEquals("/v2/a+b%2Fc/x", request.getUri().getRawPath());
        // a variable the patterns do not give is encoded once
        request = upstream("/x");
        GatewayFilters.setPath("/v2/{name}").filterRequest(exchange("/x", Map.of("name", "a b")), request);
        assertEquals("/v2/a%20b", request.getUri().getRawPath());
    }

    @Test
    void rewritePath() throws Exception {
        assertEquals("/new/a%20b?x=1", apply(GatewayFilters.rewritePath("/old/(?<rest>.*)", "/new/$\\{rest}"), "/old/a%20b?x=1"));
        assertEquals("/", apply(GatewayFilters.rewritePath("/only", ""), "/only"));
    }

    @Test
    void queryParameters() throws Exception {
        assertEquals("/p?a=1+2&b=%2B&c=x%20y", apply(GatewayFilters.addRequestParameter("c", "x y"), "/p?a=1+2&b=%2B"));
        assertEquals("/p?c=v", apply(GatewayFilters.addRequestParameter("c", "v"), "/p"));
        assertEquals("/p?a=1+2&c", apply(GatewayFilters.removeRequestParameter("b"), "/p?a=1+2&b=%2B&c&b=2"));
        assertEquals("/p", apply(GatewayFilters.removeRequestParameter("b"), "/p?b=1"));
        assertEquals("/p?a%20b=1", apply(GatewayFilters.removeRequestParameter("c"), "/p?a%20b=1&c=2"));
    }

    @Test
    void requestHeaders() throws Exception {
        MutableHttpRequest<Object> request = upstream("/x").header("X-Set", "old").header("X-Remove", "r").header("X-From", "f").header("X-Maybe", "client");
        ProxyExchange exchange = exchange("/x/abc", Map.of("seg", "abc"));
        for (GatewayFilter filter : List.of(
            GatewayFilters.addRequestHeader("X-Add", "{seg}"),
            GatewayFilters.setRequestHeader("X-Set", "new"),
            GatewayFilters.removeRequestHeader("X-Remove"),
            GatewayFilters.mapRequestHeader("X-From", "X-To"),
            GatewayFilters.addRequestHeaderIfNotPresent("X-Maybe", "gw"),
            GatewayFilters.addRequestHeaderIfNotPresent("X-Absent", "gw"))) {
            filter.filterRequest(exchange, request);
        }
        assertEquals("abc", request.getHeaders().get("X-Add"));
        assertEquals(List.of("new"), request.getHeaders().getAll("X-Set"));
        assertNull(request.getHeaders().get("X-Remove"));
        assertEquals("f", request.getHeaders().get("X-To"));
        assertEquals("client", request.getHeaders().get("X-Maybe"));
        assertEquals("gw", request.getHeaders().get("X-Absent"));
    }

    @Test
    void aHeaderValueWithALineBreakIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.addRequestHeader("X-A", "a\r\nX-B: b"));
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.addRequestHeader("X A", "a"));
        MutableHttpRequest<Object> request = upstream("/x");
        HttpStatusException injected = assertThrows(HttpStatusException.class,
            () -> GatewayFilters.addRequestHeader("X-Seg", "{seg}").filterRequest(exchange("/x", Map.of("seg", "a\r\nb")), request));
        assertEquals(400, injected.getStatus().getCode());
    }

    @Test
    void hostHeaders() throws Exception {
        MutableHttpRequest<Object> request = upstream("/x");
        ProxyExchange exchange = exchange("/x", Map.of());
        GatewayFilters.preserveHostHeader().filterRequest(exchange, request);
        assertEquals("gw.example:8443", request.getHeaders().get("Host"));
        assertTrue(exchange.isRetainHostHeader());
        GatewayFilters.setRequestHostHeader("api.example").filterRequest(exchange, request);
        assertEquals("api.example", request.getHeaders().get("Host"));
    }

    @Test
    void responseHeaders() throws Exception {
        MutableHttpResponse<?> response = HttpResponse.ok()
            .header("X-Dup", "a").header("X-Dup", "b, a")
            .header("Access-Control-Allow-Origin", "*").header("Access-Control-Allow-Origin", "https://x")
            .header("X-Rewrite", "password=secret")
            .header("X-Remove", "r");
        ProxyExchange exchange = exchange("/x", Map.of());
        for (GatewayFilter filter : List.of(
            GatewayFilters.dedupeResponseHeader(List.of("X-Dup"), GatewayFilters.DedupeStrategy.RETAIN_UNIQUE),
            GatewayFilters.dedupeResponseHeader(List.of("Access-Control-Allow-Origin"), GatewayFilters.DedupeStrategy.RETAIN_LAST),
            GatewayFilters.rewriteResponseHeader("X-Rewrite", "password=[^&]+", "password=***"),
            GatewayFilters.removeResponseHeader("X-Remove"),
            GatewayFilters.addResponseHeader("X-Added", "1"),
            GatewayFilters.setResponseHeader("X-Added", "2"))) {
            filter.filterResponse(exchange, response);
        }
        assertEquals(List.of("a", "b"), response.getHeaders().getAll("X-Dup"));
        assertEquals(List.of("https://x"), response.getHeaders().getAll("Access-Control-Allow-Origin"));
        assertEquals("password=***", response.getHeaders().get("X-Rewrite"));
        assertNull(response.getHeaders().get("X-Remove"));
        assertEquals(List.of("2"), response.getHeaders().getAll("X-Added"));
    }

    @Test
    void rewriteRequestParameter() throws Exception {
        assertEquals("/p?a=1&color=blue&b=2", apply(GatewayFilters.rewriteRequestParameter("color", "blue"), "/p?a=1&color=red&b=2&color=green"));
        assertEquals("/p?a=1", apply(GatewayFilters.rewriteRequestParameter("color", "blue"), "/p?a=1"));
        assertEquals("/p", apply(GatewayFilters.rewriteRequestParameter("color", "blue"), "/p"));
        assertEquals("/p?color=a%20b%26c", apply(GatewayFilters.rewriteRequestParameter("color", "a b&c"), "/p?color"));
    }

    @Test
    void rewriteLocationWithOptions() throws Exception {
        ProxyExchange exchange = exchange("/x", Map.of());
        MutableHttpResponse<?> response = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://up:8080/v2/items/1");
        GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.AS_IN_REQUEST, null, null, "https?").filterResponse(exchange, response);
        // the inbound path has no version: the upstream added it
        assertEquals("http://gw.example:8443/items/1", response.getHeaders().get("Location"));

        ProxyExchange versioned = exchange("/v2/x", Map.of());
        MutableHttpResponse<?> kept = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://up:8080/v2/items/1");
        GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.AS_IN_REQUEST, null, null, "https?").filterResponse(versioned, kept);
        assertEquals("http://gw.example:8443/v2/items/1", kept.getHeaders().get("Location"));

        MutableHttpResponse<?> always = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Content-Location", "http://up:8080/v10");
        GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.ALWAYS_STRIP, "Content-Location", "public.example", "https?")
            .filterResponse(versioned, always);
        assertEquals("http://public.example/", always.getHeaders().get("Content-Location"));

        MutableHttpResponse<?> notVersion = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://up:8080/v2x/items");
        GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.ALWAYS_STRIP, null, null, "https?").filterResponse(exchange, notVersion);
        assertEquals("http://gw.example:8443/v2x/items", notVersion.getHeaders().get("Location"));

        MutableHttpResponse<?> ftp = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "ftp://up:8080/file");
        GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.NEVER_STRIP, null, null, "https?").filterResponse(exchange, ftp);
        assertEquals("ftp://up:8080/file", ftp.getHeaders().get("Location"));

        assertThrows(IllegalArgumentException.class,
            () -> GatewayFilters.rewriteLocationResponseHeader(GatewayFilters.StripVersion.NEVER_STRIP, null, "bad host\r\nX: y", "https?"));
    }

    @Test
    void fallbackHeadersCarryTheFailure() throws Exception {
        HttpRequest<?> inbound = HttpRequest.GET("/x").header("Host", "gw.example");
        GatewayRoute route = GatewayRoute.builder("r").uri("http://up").build();
        GatewayRequestContext context = GatewayRequestContext.of(inbound, route);
        ProxyExchange exchange = new ProxyExchange(inbound, new MatchedPathVariables(Map.of(), ConversionService.SHARED), "r", URI.create("http://up"), true, List.of());

        MutableHttpRequest<Object> untouched = upstream("/x");
        GatewayFilters.fallbackHeaders().filterRequest(exchange, untouched);
        assertNull(untouched.getHeaders().get("Execution-Exception-Type"));

        context.failure(new IllegalStateException("upstream\r\nX-Injected: 1", new java.io.IOException("reset")));
        MutableHttpRequest<Object> request = upstream("/x");
        GatewayFilters.fallbackHeaders().filterRequest(exchange, request);
        assertEquals("java.lang.IllegalStateException", request.getHeaders().get("Execution-Exception-Type"));
        assertEquals("upstream  X-Injected: 1", request.getHeaders().get("Execution-Exception-Message"));
        assertEquals("java.io.IOException", request.getHeaders().get("Root-Cause-Exception-Type"));
        assertEquals("reset", request.getHeaders().get("Root-Cause-Exception-Message"));
    }

    @Test
    void rewriteLocation() throws Exception {
        ProxyExchange exchange = exchange("/x", Map.of());
        MutableHttpResponse<?> response = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://up:8080/items/1?x=%2B#f");
        GatewayFilters.rewriteLocationResponseHeader().filterResponse(exchange, response);
        assertEquals("http://gw.example:8443/items/1?x=%2B#f", response.getHeaders().get("Location"));
        MutableHttpResponse<?> other = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "http://elsewhere/items");
        GatewayFilters.rewriteLocationResponseHeader().filterResponse(exchange, other);
        assertEquals("http://elsewhere/items", other.getHeaders().get("Location"));
        MutableHttpResponse<?> relative = HttpResponse.status(io.micronaut.http.HttpStatus.FOUND).header("Location", "/items");
        GatewayFilters.rewriteLocationResponseHeader().filterResponse(exchange, relative);
        assertEquals("/items", relative.getHeaders().get("Location"));
    }

    @Test
    void theFiltersDescribeThemselvesForTheEndpoint() {
        assertEquals("StripPrefix=2", GatewayFilters.stripPrefix(2).toString());
        assertEquals("AddRequestHeader=X-A, v", GatewayFilters.addRequestHeader("X-A", "v").toString());
        assertEquals("RedirectTo=302, https://x", GatewayFilters.redirectTo(302, "https://x").toString());
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.redirectTo(200, "https://x"));
        assertFalse(GatewayFilters.secureHeaders().toString().isEmpty());
    }

    @Test
    void aReservedExpansionOfADecodedVariableKeepsAQuestionMarkAndAHashInThePath() throws Exception {
        // a variable of a functional route has no raw value: its decoded value is encoded again
        MutableHttpRequest<Object> request = upstream("/fn/x");
        GatewayFilters.setPath("/inspect/{+rest}").filterRequest(exchange("/fn/x", Map.of("rest", "a?b=1#f")), request);
        assertEquals("/inspect/a%3Fb=1%23f", request.getUri().getRawPath());
        assertNull(request.getUri().getRawQuery());
        assertNull(request.getUri().getRawFragment());
        request = upstream("/fn/x");
        GatewayFilters.prefixPath("/{+rest}").filterRequest(exchange("/fn/x", Map.of("rest", "p?q")), request);
        assertEquals("/p%3Fq/fn/x", request.getUri().getRawPath());
        assertNull(request.getUri().getRawQuery());
    }

    @Test
    void theRawValuesOfATemplateWhoseConstraintThePathDoesNotMeetAreNotUsed() throws Exception {
        // the router selects /{other}/{id} for /alpha/x: the captures of /{id:[0-9]+}/x are not its variables
        assertNull(GatewayPathPattern.parse("/{id:[0-9]+}/x").rawVariables("/alpha/x"));
        assertEquals(Map.of("id", "42"), GatewayPathPattern.parse("/{id:[0-9]+}/x").rawVariables("/42/x"));
        MutableHttpRequest<Object> request = upstream("/alpha/x");
        GatewayFilters.setPath("/v/{id}").filterRequest(
            exchange("/alpha/x", Map.of("other", "alpha", "id", "x"), "/{id:[0-9]+}/x", "/{other}/{id}"), request);
        assertEquals("/v/x", request.getUri().getRawPath());
    }

    @Test
    void dedupingSetCookieKeepsTheCommaOfItsExpiresAttribute() throws Exception {
        MutableHttpResponse<?> response = HttpResponse.ok()
            .header("Set-Cookie", "a=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT")
            .header("Set-Cookie", "a=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT");
        GatewayFilters.dedupeResponseHeader(List.of("Set-Cookie"), GatewayFilters.DedupeStrategy.RETAIN_UNIQUE).filterResponse(null, response);
        assertEquals(List.of("a=1; Expires=Wed, 21 Oct 2026 07:28:00 GMT"), response.getHeaders().getAll("Set-Cookie"));
    }

    @Test
    void setStatusRefusesAnInformationalStatus() {
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.setStatus(101));
        assertThrows(IllegalArgumentException.class, () -> GatewayFilters.setStatus(103));
        assertEquals("SetStatus=204", GatewayFilters.setStatus(204).toString());
    }

    @Test
    void theCredentialsOfARouteUriAreNotPrinted() {
        GatewayRoute route = GatewayRoute.builder("secret").uri("http://user:pa55@up.example:8080/base").build();
        assertFalse(route.toString().contains("pa55"), route.toString());
        assertEquals("http://***@up.example:8080/base", RelayHeaders.redact(route.getUri()));
        assertEquals("http://up.example", RelayHeaders.redact(URI.create("http://up.example")));
    }

    @Test
    void aHostIsAHostAndAnOptionalPort() {
        for (String valid : List.of("example.com", "example.com:8080", "10.0.0.1:1", "[::1]", "[::1]:8443", "[fe80::1%25eth0]:80", "a_b.local")) {
            assertNull(RelayHeaders.hostRejection(valid), valid);
        }
        for (String invalid : List.of("", "[::1", "[::1]x", "[example.com]", "example.com:abc", "example.com:", "example.com:99999",
            "a:b:c", "evil.example/path", "user@evil.example", "[::1]:8080:1")) {
            assertTrue(RelayHeaders.hostRejection(invalid) != null, invalid);
        }
    }

    @Test
    void theTrailersThatWouldBeReadAsHeadersAreNotRelayed() {
        io.micronaut.http.simple.SimpleHttpHeaders trailers = new io.micronaut.http.simple.SimpleHttpHeaders(ConversionService.SHARED);
        trailers.add("grpc-status", "0");
        trailers.add("X-Forwarded-For", "6.6.6.6");
        trailers.add("X_Forwarded_For", "6.6.6.6");
        trailers.add("Proxy-Authorization", "Basic x");
        trailers.add("Authorization", "Bearer x");
        trailers.add("Set-Cookie", "a=b");
        trailers.add("Content-Length", "1");
        trailers.add("x-http2-stream-id", "3");
        io.micronaut.http.HttpHeaders sanitized = RelayHeaders.sanitizeTrailers(trailers);
        assertEquals(java.util.Set.of("grpc-status"), java.util.Set.copyOf(sanitized.names()));
    }
}
