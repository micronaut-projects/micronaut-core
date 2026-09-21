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
