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

import io.micronaut.context.ApplicationContext;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Header values that are not a host, port or supported scheme are not used for host resolution.
 */
class DefaultHttpHostResolverHeaderValuesTest {

    private static final Map<Map<String, Object>, ApplicationContext> CONTEXTS = new ConcurrentHashMap<>();
    private static final Map<String, Object> CONFIGURED_HEADERS = Map.of(
        "micronaut.server.host-resolution.host-header", "X-Original-Host",
        "micronaut.server.host-resolution.protocol-header", "X-Original-Proto");

    @ParameterizedTest
    @ValueSource(strings = {"first.example.com@second.example.com", "first.example.com/.second.example.com", "first example.com", "", "first.example.com;x=1"})
    void anXForwardedHostThatIsNoHostFallsBackToTheHostHeader(String value) {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header("X-Forwarded-Host", value)
            .header("X-Forwarded-Proto", "https");
        assertEquals("http://origin.example.com", resolve(Map.of(), request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://second.example.com/?x=", "https:", "1http", "ftp"})
    void anXForwardedProtoThatIsNoSchemeFallsBackToTheHostHeader(String value) {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header("X-Forwarded-Host", "xfwd.example.com")
            .header("X-Forwarded-Proto", value);
        assertEquals("http://origin.example.com", resolve(Map.of(), request));
    }

    @Test
    void aForwardedHostThatIsNoHostFallsBackToTheHostHeader() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=\"first.example.com@second.example.com\";proto=https");
        assertEquals("http://origin.example.com", resolve(Map.of(), request));
    }

    // The parser skips an element whose host has a malformed port and uses the next one, while a host that is
    // not a valid authority makes the resolver ignore the proxy headers. Whether both should skip, or both
    // reject, is open: this pins the current, stricter, behaviour for the second case.
    @Test
    void aForwardedElementWithAnInvalidAuthorityIsNotSkippedForTheNextElement() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=\"first@example.com\";proto=http, host=second.example.com;proto=https");
        assertEquals("http://origin.example.com", resolve(Map.of(), request));
    }

    @Test
    void aForwardedElementWithAMalformedPortIsSkippedForTheNextElement() {
        HttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=\"first.example.com:bad\";proto=http, host=second.example.com;proto=https");
        assertEquals("https://second.example.com", resolve(Map.of(), request));
    }

    @Test
    void validHostsAreStillUsed() {
        assertEquals("https://xfwd-1.example_host~a.com:8443", resolve(Map.of(), HttpRequest.GET("/")
            .header("X-Forwarded-Host", "xfwd-1.example_host~a.com:8443")
            .header("X-Forwarded-Proto", "https")));
        assertEquals("http://[2001:db8::1]:8080", resolve(Map.of(), HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "[2001:db8::1]:8080")));
        assertEquals("http://192.0.2.1", resolve(Map.of(), HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "192.0.2.1")));
        assertEquals("wss://my_service:8080", resolve(Map.of(), HttpRequest.GET("/")
            .header("X-Forwarded-Host", "my_service:8080")
            .header("X-Forwarded-Proto", "wss")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"first.example.com@second.example.com", "first.example.com/.second.example.com", "[2001:db8::1",
        "first.example.com,second.example.com", "first.example.com;x=1"})
    void aHostHeaderThatIsNoHostFallsBackToTheRequestUri(String value) {
        HttpRequest<?> request = HttpRequest.GET("https://uri.example.com/path")
            .header(HttpHeaders.HOST, value);
        assertEquals("https://uri.example.com", resolve(Map.of(), request));
    }

    @Test
    void configuredHeadersThatAreNoHostOrSchemeFallBackToTheRequestUri() {
        HttpRequest<?> request = HttpRequest.GET("https://uri.example.com/path")
            .header("X-Original-Host", "first.example.com/.second.example.com")
            .header("X-Original-Proto", "https://second.example.com/?x=");
        assertEquals("https://uri.example.com", resolve(CONFIGURED_HEADERS, request));
    }

    @Test
    void configuredHostHeaderWithoutPortInHostAcceptsANumericPort() {
        assertEquals("http://configured.example.com:8080", resolve(CONFIGURED_HEADERS, HttpRequest.GET("http://uri.example.com/")
            .header("X-Original-Host", "configured.example.com:8080")));
        assertEquals("http://configured.example.com:65535", resolve(CONFIGURED_HEADERS, HttpRequest.GET("http://uri.example.com/")
            .header("X-Original-Host", "configured.example.com:65535")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"configured.example.com:8080@second.example.com", "configured.example.com:http", "configured.example.com:",
        "configured.example.com:0", "configured.example.com:65536", "configured.example.com:99999999999",
        "configured.example.com,second.example.com", "configured.example.com "})
    void configuredHostHeaderWithoutPortInHostFallsBackToTheRequestUriWhenItIsNoSingleHostAndPort(String value) {
        assertEquals("http://uri.example.com", resolve(CONFIGURED_HEADERS, HttpRequest.GET("http://uri.example.com/")
            .header("X-Original-Host", value)));
    }

    @AfterAll
    static void closeContexts() {
        CONTEXTS.values().forEach(ApplicationContext::close);
        CONTEXTS.clear();
    }

    private static String resolve(Map<String, Object> config, HttpRequest<?> request) {
        return CONTEXTS.computeIfAbsent(Map.copyOf(config), ApplicationContext::run)
            .getBean(HttpHostResolver.class)
            .resolve(request);
    }
}
