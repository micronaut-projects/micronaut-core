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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The path rules of the gateway and the translation of its path patterns.
 */
class ProxyPathsTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "/a/../b", "/a/./b", "/..", "/.", "/a/%2e%2e/b", "/a/%2E%2e", "/a/.%2e/b", "/a/%2e/b",
        "/a%2Fb", "/a%2fb", "/a%5Cb", "/a\\b", "/a;b", "/a/..;/b", "/a%00b", "/a%0Ab", "/a%0db",
        "/a%GG", "/a%2", "/a%", "a/b", "", "/a%C0%AEb", "/a%c1%9c"
    })
    void aPathThatCouldRouteElsewhereIsRejected(String path) {
        assertNotNull(ProxyPaths.rejection(path), path);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/", "/a", "/a/b/", "/a..b", "/.../x", "/..../x", "/a.", "/.a", "/a%20b", "/a%252e%252e", "/a+b", "/%E2%80%93",
        "/a%3Bb", "//a///b", "/a:b", "/a@b", "/a=b&c"
    })
    void aPathThatRoutesTheSameEverywhereIsRelayed(String path) {
        assertNull(ProxyPaths.rejection(path), path);
    }

    @Test
    void segmentsAndPrefixes() {
        assertEquals(List.of("a", "b", ""), ProxyPaths.segments("/a/b/"));
        assertEquals(List.of(), ProxyPaths.segments("/"));
        assertEquals("/v2/x", ProxyPaths.prefix("/v2", "/x"));
        assertEquals("/v2/x", ProxyPaths.prefix("v2/", "x"));
        assertEquals("/p?", ProxyPaths.pathAndQuery("/p", ""));
        assertEquals("/p", ProxyPaths.pathAndQuery("/p", null));
    }

    @Test
    void gatewayPathPatterns() {
        GatewayPathPattern prefix = GatewayPathPattern.parse("/api/orders/**");
        assertEquals("/api/orders", prefix.template());
        assertEquals(true, prefix.prefix());
        assertEquals(Map.of(), prefix.rawVariables("/api/orders/1"));
        assertEquals(Map.of(), prefix.rawVariables("/api/orders"));
        assertNull(prefix.rawVariables("/api/ordersX"));
        GatewayPathPattern root = GatewayPathPattern.parse("/**");
        assertEquals("/", root.template());
        GatewayPathPattern variables = GatewayPathPattern.parse("/t/{tenant}/*/items/{id:[0-9]+}");
        assertEquals("/t/{tenant:[^/]+}/{_w0:[^/]+}/items/{id:[0-9]+}", variables.template());
        assertEquals(false, variables.prefix());
        assertEquals(Map.of("tenant", "a+b", "id", "12"), variables.rawVariables("/t/a+b/x/items/12"));
        assertEquals(Map.of("tenant", "a%20b", "id", "12"), variables.rawVariables("/t/a%20b/x/items/12/"));
        GatewayPathPattern prefixWithVariable = GatewayPathPattern.parse("/t/{tenant}/**");
        assertEquals(Map.of("tenant", "x"), prefixWithVariable.rawVariables("/t/x/deep/path"));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathPattern.parse("/a/**/b"));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathPattern.parse("/a/b*"));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathPattern.parse("/a/?"));
        assertThrows(IllegalArgumentException.class, () -> GatewayPathPattern.parse("a/b"));
    }

    @Test
    void anUpstreamPathWithAnEmptyLeadingSegmentIsRejected() {
        assertNotNull(ProxyPaths.upstreamRejection("//attacker.example/x"));
        assertNotNull(ProxyPaths.upstreamRejection("//"));
        assertNotNull(ProxyPaths.upstreamRejection("/a/../b"));
        assertNull(ProxyPaths.upstreamRejection("/a//b"));
        assertNull(ProxyPaths.upstreamRejection("/"));
        assertThrows(ProxyPaths.UpstreamPathException.class, () -> ProxyPaths.uri("//attacker.example/x", null));
        assertEquals("/a//b?q", ProxyPaths.uri("/a//b", "q").toString());
        assertNull(ProxyPaths.uri("/a//b", null).getRawAuthority());
    }

    @Test
    void aReservedExpansionMatchesTheRestOfThePath() {
        GatewayPathPattern rest = GatewayPathPattern.parse("/files/{+rest}");
        assertEquals("/files/{+rest}", rest.template());
        assertEquals(java.util.List.of("rest"), rest.variables());
        assertEquals(Map.of("rest", "a/b/c"), rest.rawVariables("/files/a/b/c"));
        assertEquals(Map.of("rest", "a"), rest.rawVariables("/files/a"));
        assertNull(rest.rawVariables("/files"));
    }

    @Test
    void hostHeaders() {
        assertNull(DefaultProxyHandler.hostRejection(io.micronaut.http.HttpRequest.GET("/").header("Host", "api.example.com:8443")));
        assertNull(DefaultProxyHandler.hostRejection(io.micronaut.http.HttpRequest.GET("/").header("Host", "[::1]:80")));
        assertNotNull(DefaultProxyHandler.hostRejection(io.micronaut.http.HttpRequest.GET("/").header("Host", "a/b")));
        assertNotNull(DefaultProxyHandler.hostRejection(io.micronaut.http.HttpRequest.GET("/").header("Host", "u@h")));
        assertNotNull(DefaultProxyHandler.hostRejection(io.micronaut.http.HttpRequest.GET("/").header("Host", "a").header("Host", "b")));
    }
}
