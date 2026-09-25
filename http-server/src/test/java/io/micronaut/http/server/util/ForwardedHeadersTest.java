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
package io.micronaut.http.server.util;

import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ForwardedHeadersTest {

    @Test
    void writesThisHop() {
        HttpRequest<?> inbound = inbound("203.0.113.7", false, "gateway.example:8080");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.apply(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("http", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("gateway.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("8080", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertFalse(headers.contains(ForwardedHeaders.X_FORWARDED_PREFIX));
        assertEquals("for=203.0.113.7;proto=http;host=\"gateway.example:8080\"", headers.get(HttpHeaders.FORWARDED));
    }

    @Test
    void defaultPortFollowsTheScheme() {
        HttpRequest<?> inbound = inbound("203.0.113.7", true, "gateway.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.apply(inbound, outbound);

        assertEquals("https", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("443", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("for=203.0.113.7;proto=https;host=gateway.example", outbound.getHeaders().get(HttpHeaders.FORWARDED));
    }

    @Test
    void untrustedPeerHeadersAreReplaced() {
        HttpRequest<?> inbound = inbound("203.0.113.7", false, "gateway.example",
            ForwardedHeaders.X_FORWARDED_FOR, "10.0.0.1",
            ForwardedHeaders.X_FORWARDED_HOST, "forged.example",
            HttpHeaders.FORWARDED, "for=10.0.0.1");
        // the outbound request was copied from the inbound one, headers included
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders")
            .header(ForwardedHeaders.X_FORWARDED_FOR, "10.0.0.1")
            .header(HttpHeaders.FORWARDED, "for=10.0.0.1");

        ForwardedHeaders.apply(inbound, outbound);

        assertEquals(List.of("203.0.113.7"), outbound.getHeaders().getAll(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("gateway.example", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals(List.of("for=203.0.113.7;proto=http;host=gateway.example"), outbound.getHeaders().getAll(HttpHeaders.FORWARDED));
    }

    @Test
    void trustedProxyHeadersAreAppendedTo() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example",
            ForwardedHeaders.X_FORWARDED_PORT, "443",
            ForwardedHeaders.X_FORWARDED_PREFIX, "/shop",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=shop.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .prefix("/api")
            .build()
            .write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("/shop/api", headers.get(ForwardedHeaders.X_FORWARDED_PREFIX));
        assertEquals("for=203.0.113.7;proto=https;host=shop.example, for=10.0.0.2;proto=http;host=gateway.internal", headers.get(HttpHeaders.FORWARDED));
    }

    @Test
    void trustedValuesAreReadBeforeSharedHeadersChange() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example",
            ForwardedHeaders.X_FORWARDED_PORT, "443",
            ForwardedHeaders.X_FORWARDED_PREFIX, "/shop",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=shop.example");
        // the outbound request shares the headers of the inbound one, like a mutated Netty server request
        MutableHttpRequest<?> outbound = (MutableHttpRequest<?>) ((HttpRequestWrapper<?>) inbound).getDelegate();

        ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .build()
            .write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("/shop", headers.get(ForwardedHeaders.X_FORWARDED_PREFIX));
        assertEquals("for=203.0.113.7;proto=https;host=shop.example, for=10.0.0.2;proto=http;host=gateway.internal", headers.get(HttpHeaders.FORWARDED));
    }

    @Test
    void ipv6ClientIsQuotedInForwarded() {
        HttpRequest<?> inbound = inbound("2001:db8::17", false, "gateway.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.builder().xForwarded(false).build().write(inbound, outbound);

        assertFalse(outbound.getHeaders().contains(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("for=\"[2001:db8:0:0:0:0:0:17]\";proto=http;host=gateway.example", outbound.getHeaders().get(HttpHeaders.FORWARDED));
    }

    @Test
    void trustedXForwardedOnlyChainIsKeptInForwarded() {
        // the trusted proxy in front only writes X-Forwarded-*: the Forwarded header written for
        // this hop must not shadow the original client, scheme and host
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example",
            ForwardedHeaders.X_FORWARDED_PORT, "443");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("for=203.0.113.7;proto=https;host=shop.example, for=10.0.0.2;proto=http;host=gateway.internal", headers.get(HttpHeaders.FORWARDED));
        // the default port of the scheme is not repeated in the host
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", null);
    }

    @Test
    void trustedXForwardedChainIsTranslatedWhenOnlyForwardedIsWritten() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7, 2001:db8::17",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example",
            ForwardedHeaders.X_FORWARDED_PORT, "8443");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .xForwarded(false)
            .build()
            .write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertFalse(headers.contains(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("for=203.0.113.7;proto=https;host=\"shop.example:8443\", for=\"[2001:db8::17]\", for=10.0.0.2;proto=http;host=gateway.internal",
            headers.get(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", 8443);
    }

    @Test
    void trustedForwardedOnlyChainIsKeptInXForwarded() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=\"shop.example:8443\", for=\"[2001:db8::17]\";by=_edge");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 2001:db8::17, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("8443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("for=203.0.113.7;proto=https;host=\"shop.example:8443\", for=\"[2001:db8::17]\";by=_edge, for=10.0.0.2;proto=http;host=gateway.internal",
            headers.get(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", 8443);

        // a downstream that only reads the X-Forwarded-* headers resolves the same client
        MutableHttpRequest<?> xForwardedOnly = HttpRequest.GET("http://upstream/orders");
        ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .forwarded(false)
            .build()
            .write(inbound, xForwardedOnly);
        assertFalse(xForwardedOnly.getHeaders().contains(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(xForwardedOnly, "203.0.113.7", "https", "shop.example", 8443);
    }

    @Test
    void trustedChainInBothFormatsIsAppendedToEach() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=shop.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("for=203.0.113.7;proto=https;host=shop.example, for=10.0.0.2;proto=http;host=gateway.internal", headers.get(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", null);
    }

    @Test
    void untrustedXForwardedOnlyChainIsNotTranslated() {
        HttpRequest<?> inbound = inbound("203.0.113.7", false, "gateway.example",
            ForwardedHeaders.X_FORWARDED_FOR, "10.0.0.1",
            ForwardedHeaders.X_FORWARDED_PROTO, "https");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        assertEquals("203.0.113.7", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("for=203.0.113.7;proto=http;host=gateway.example", outbound.getHeaders().get(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "http", "gateway.example", null);
    }

    @Test
    void missingForwardedPortFollowsTheForwardedScheme() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        assertEquals("https", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PORT));
    }

    @Test
    void missingForwardedPortFollowsTheForwardedHost() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example:8443");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        assertEquals("8443", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PORT));
    }

    @Test
    void missingForwardedPortWithOnlyAForwardedHostFollowsThisScheme() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        assertEquals("http", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("80", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PORT));
    }

    @Test
    void untrustedForwardedHostDoesNotChangeThePort() {
        HttpRequest<?> inbound = inbound("203.0.113.7", false, "gateway.internal:8080",
            ForwardedHeaders.X_FORWARDED_PROTO, "https",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        assertEquals("http", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("gateway.internal", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("8080", outbound.getHeaders().get(ForwardedHeaders.X_FORWARDED_PORT));
    }

    @Test
    void trustedForwardedFillsWhatPartialXForwardedHeadersLack() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=shop.example",
            ForwardedHeaders.X_FORWARDED_PROTO, "https");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("https", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertEquals("for=203.0.113.7;proto=https;host=shop.example, for=10.0.0.2;proto=http;host=\"gateway.internal:8080\"", headers.get(HttpHeaders.FORWARDED));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", null);
    }

    @Test
    void trustedForwardedFillsPartialXForwardedHeadersWhenOnlyXForwardedIsWritten() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            HttpHeaders.FORWARDED, "for=203.0.113.7;proto=https;host=shop.example",
            ForwardedHeaders.X_FORWARDED_PROTO, "https");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .forwarded(false)
            .build()
            .write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertFalse(headers.contains(HttpHeaders.FORWARDED));
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        assertEquals("shop.example", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
        assertResolvesOriginalClient(outbound, "203.0.113.7", "https", "shop.example", 443);
    }

    @Test
    void xForwardedValuesWinOverTheForwardedHeader() {
        HttpRequest<?> inbound = inbound("10.0.0.2", false, "gateway.internal:8080",
            HttpHeaders.FORWARDED, "for=198.51.100.1;proto=http;host=other.example",
            ForwardedHeaders.X_FORWARDED_FOR, "203.0.113.7",
            ForwardedHeaders.X_FORWARDED_HOST, "shop.example:8443");
        MutableHttpRequest<?> outbound = HttpRequest.GET("http://upstream/orders");

        trustingPrivateNetwork().write(inbound, outbound);

        HttpHeaders headers = outbound.getHeaders();
        assertEquals("203.0.113.7, 10.0.0.2", headers.get(ForwardedHeaders.X_FORWARDED_FOR));
        // the scheme only the Forwarded header gives
        assertEquals("http", headers.get(ForwardedHeaders.X_FORWARDED_PROTO));
        assertEquals("shop.example:8443", headers.get(ForwardedHeaders.X_FORWARDED_HOST));
        assertEquals("8443", headers.get(ForwardedHeaders.X_FORWARDED_PORT));
    }

    private static ForwardedHeaders trustingPrivateNetwork() {
        return ForwardedHeaders.builder()
            .trustedProxy(address -> address.getHostString().startsWith("10."))
            .build();
    }

    /**
     * Resolve the client, scheme and host the way the upstream server does.
     */
    private static void assertResolvesOriginalClient(MutableHttpRequest<?> outbound, String client, String scheme, String host, Integer port) {
        HttpRequest<?> received = inbound("10.0.0.3", false, "upstream",
            outbound.getHeaders().asMap().entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().flatMap(value -> java.util.stream.Stream.of(entry.getKey(), value)))
                .toArray(String[]::new));
        assertEquals(client, new DefaultHttpClientAddressResolver(new HttpServerConfiguration()).resolve(received));
        ProxyHeaderParser parser = new ProxyHeaderParser(received);
        assertEquals(scheme, parser.getScheme());
        assertEquals(host, parser.getHost());
        assertEquals(port, parser.getPort());
    }

    private static HttpRequest<?> inbound(String remoteAddress, boolean secure, String host, String... headers) {
        MutableHttpRequest<Object> request = HttpRequest.GET("/orders");
        request.header(HttpHeaders.HOST, host);
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        InetSocketAddress address = new InetSocketAddress(remoteAddress, 50000);
        return new HttpRequestWrapper<Object>(request) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                return address;
            }

            @Override
            public boolean isSecure() {
                return secure;
            }
        };
    }
}
