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
import io.micronaut.context.env.PropertySource;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.HttpServerConfiguration.HostResolutionConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code micronaut.server.host-resolution.allow-forwarded} and {@code allow-x-forwarded} allow ignoring
 * the forwarded headers for host resolution. Both default to {@code true}.
 */
class DefaultHttpHostResolverForwardedHeadersTest {

    private static final String ALLOW_FORWARDED = "micronaut.server.host-resolution.allow-forwarded";
    private static final String ALLOW_X_FORWARDED = "micronaut.server.host-resolution.allow-x-forwarded";
    private static final Map<Map<String, Object>, ApplicationContext> CONTEXTS = new ConcurrentHashMap<>();

    @Test
    void byDefaultTheXForwardedHeadersAreReadAheadOfTheHostHeader() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header("X-Forwarded-Host", "xfwd.example.com")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Port", "8443");
        assertEquals("https://xfwd.example.com:8443", resolve(Map.of(), request));
    }

    @Test
    void byDefaultTheForwardedHeaderIsReadAheadOfTheHostHeader() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=https");
        assertEquals("https://fwd.example.com", resolve(Map.of(), request));
    }

    @Test
    void byDefaultTheForwardedHeaderTakesPrecedenceOverTheXForwardedHeaders() {
        assertEquals("https://fwd.example.com", resolve(Map.of(), bothFamilies()));
    }

    @Test
    void explicitlyAllowingBothFamiliesIsTheDefault() {
        Map<String, Object> config = Map.of(ALLOW_FORWARDED, true, ALLOW_X_FORWARDED, true);
        assertEquals("https://fwd.example.com", resolve(config, bothFamilies()));
        assertEquals("https://xfwd.example.com:8443", resolve(config, xForwardedOnly()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-x-forwarded={1}")
    @CsvSource({
        "true,  true,  https://fwd.example.com",
        "false, true,  https://xfwd.example.com:8443",
        "true,  false, https://fwd.example.com",
        "false, false, http://origin.example.com",
    })
    void withBothFamiliesPresent(boolean allowForwarded, boolean allowXForwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXForwarded), bothFamilies()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-x-forwarded={1}")
    @CsvSource({
        "true,  true,  https://fwd.example.com",
        "false, true,  http://origin.example.com",
        "true,  false, https://fwd.example.com",
        "false, false, http://origin.example.com",
    })
    void withOnlyTheForwardedHeaderPresent(boolean allowForwarded, boolean allowXForwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXForwarded), forwardedOnly()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-x-forwarded={1}")
    @CsvSource({
        "true,  true,  https://xfwd.example.com:8443",
        "false, true,  https://xfwd.example.com:8443",
        "true,  false, http://origin.example.com",
        "false, false, http://origin.example.com",
    })
    void withOnlyTheXForwardedHeadersPresent(boolean allowForwarded, boolean allowXForwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXForwarded), xForwardedOnly()));
    }

    @Test
    void theSchemeIsNotTakenFromAForwardedHeaderThatIsNotAllowed() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "proto=https")
            .header("X-Forwarded-Host", "xfwd.example.com");
        assertEquals("http://xfwd.example.com", resolve(flags(false, true), request));
    }

    @Test
    void aForwardedHostIsNotCombinedWithTheXForwardedSchemeAndPort() {
        // as without the flags: the scheme and port come from the same header family as the host
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Port", "8443");
        assertEquals("http://fwd.example.com", resolve(flags(true, false), request));
    }

    @Test
    void byDefaultAForwardedHeaderWithoutHostStillHidesTheXForwardedHeaders() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "for=198.51.100.9")
            .header("X-Forwarded-Host", "xfwd.example.com");
        assertEquals("http://origin.example.com", resolve(Map.of(), request));
        assertEquals("http://xfwd.example.com", resolve(flags(false, true), request));
    }

    @Test
    void withNoFamilyAllowedAndNoHostHeaderTheRequestUriIsUsed() {
        MutableHttpRequest<?> request = HttpRequest.GET("https://www.example.com/test")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=http")
            .header("X-Forwarded-Host", "xfwd.example.com");
        assertEquals("https://www.example.com", resolve(flags(false, false), request));
    }

    @Test
    void withNoFamilyAllowedAndNoHostInTheRequestTheEmbeddedHostIsUsed() {
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=https")
            .header("X-Forwarded-Host", "xfwd.example.com")
            .header("X-Forwarded-Proto", "https");
        // no embedded server runs in this context
        assertEquals("http://localhost", resolve(flags(false, false), request));
    }

    @Test
    void withoutHostResolutionConfigurationBothFamiliesAreRead() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            assertNull(ctx.getBean(HttpServerConfiguration.class).getHostResolution());
            HttpHostResolver resolver = ctx.getBean(HttpHostResolver.class);
            assertEquals("https://fwd.example.com", resolver.resolve(forwardedOnly()));
            assertEquals("https://xfwd.example.com:8443", resolver.resolve(xForwardedOnly()));
        }
    }

    @Test
    void severalForwardedHeadersAreIgnoredWhenNotAllowed() {
        MutableHttpRequest<?> request = xForwardedOnly()
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=https")
            .header(HttpHeaders.FORWARDED, "host=other.example.com, host=another.example.com");
        assertEquals("https://xfwd.example.com:8443", resolve(flags(false, true), request));
        assertEquals("http://origin.example.com", resolve(flags(false, false), request));
    }

    @Test
    void aSingleConfiguredHostHeaderIsReadWhateverTheFlags() {
        Map<String, Object> config = new HashMap<>(flags(false, false));
        config.put("micronaut.server.host-resolution.host-header", "X-Forwarded-Host");
        assertEquals("http://xfwd.example.com", resolve(config, xForwardedOnly()));
    }

    @Test
    void theHostHeaderCanBeCombinedWithAConfiguredProtocolHeader() {
        // the configuration the guide shows for a proxy that overwrites X-Forwarded-Proto only
        Map<String, Object> config = Map.of(
            "micronaut.server.host-resolution.host-header", "Host",
            "micronaut.server.host-resolution.port-in-host", true,
            "micronaut.server.host-resolution.protocol-header", "X-Forwarded-Proto");
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com:8443")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Host", "other.example.com")
            .header("X-Forwarded-Port", "9000");
        assertEquals("https://origin.example.com:8443", resolve(config, request));
        assertEquals("https://origin.example.com", resolve(config, HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header("X-Forwarded-Proto", "https")));
    }

    @Test
    void configuredHeadersAreReadWhateverTheFlags() {
        Map<String, Object> config = new HashMap<>(flags(false, false));
        config.put("micronaut.server.host-resolution.host-header", "X-Forwarded-Host");
        config.put("micronaut.server.host-resolution.protocol-header", "X-Forwarded-Proto");
        config.put("micronaut.server.host-resolution.port-header", "X-Forwarded-Port");
        assertEquals("https://xfwd.example.com:8443", resolve(config, xForwardedOnly()));
    }

    @Test
    void aForwardedHostThatIsNotAllowedResolvesToTheDefaultHost() {
        Map<String, Object> config = Map.of("micronaut.server.host-resolution.allowed-hosts", "https://app\\.example\\.com");
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "app.example.com")
            .header("X-Forwarded-Host", "other.example.com")
            .header("X-Forwarded-Proto", "https");
        assertEquals("http://localhost", resolve(config, request));
    }

    @Test
    void allowedHostsApplyToTheHostHeaderWhenTheForwardedHeadersAreIgnored() {
        Map<String, Object> config = new HashMap<>(flags(false, false));
        config.put("micronaut.server.host-resolution.allowed-hosts", "http://app\\.example\\.com");
        MutableHttpRequest<?> allowed = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "app.example.com")
            .header(HttpHeaders.FORWARDED, "host=other.example.com")
            .header("X-Forwarded-Host", "other.example.com");
        assertEquals("http://app.example.com", resolve(config, allowed));

        MutableHttpRequest<?> notAllowed = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "other.example.com")
            .header("X-Forwarded-Host", "app.example.com");
        assertEquals("http://localhost", resolve(config, notAllowed));
    }

    @Test
    void bothFlagsDefaultToTrue() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of("micronaut.server.host-resolution.port-in-host", true))) {
            HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
            assertNotNull(configuration);
            assertTrue(configuration.isAllowForwarded());
            assertTrue(configuration.isAllowXForwarded());
            assertFalse(configuration.headersConfigured());
        }
    }

    @Test
    void bothFlagsBindFromTheirKebabCaseNames() {
        try (ApplicationContext ctx = ApplicationContext.run(flags(false, false))) {
            HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
            assertNotNull(configuration, "setting only the flags creates the host resolution configuration");
            assertFalse(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
            assertFalse(configuration.headersConfigured(), "the flags do not switch to the configured headers");
        }
    }

    // The rows that expect true document a known limitation: the name derived from setAllowXForwarded
    // (allow-xforwarded, or allowXForwarded) does not bind, only the documented allow-x-forwarded does.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "micronaut.server.host-resolution.allow-x-forwarded, false",
        "micronaut.server.host-resolution.allow-xforwarded,  true",
        "micronaut.server.host-resolution.allowXForwarded,   true",
    })
    void onlyTheHyphenatedNameBindsTheXForwardedFlag(String property, boolean expected) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(property, false))) {
            HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
            boolean actual = configuration == null || configuration.isAllowXForwarded();
            assertEquals(expected, actual);
        }
    }

    @Test
    void eachFlagBindsIndependently() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(ALLOW_X_FORWARDED, false))) {
            HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
            assertNotNull(configuration);
            assertTrue(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
        }
    }

    @Test
    void bothFlagsBindFromEnvironmentVariables() {
        PropertySource env = PropertySource.of("env", Map.of(
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_FORWARDED", "false",
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_X_FORWARDED", "false"
        ), PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, PropertySource.Origin.of("env"));
        try (ApplicationContext ctx = ApplicationContext.builder().propertySources(env).start()) {
            HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
            assertNotNull(configuration);
            assertFalse(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
        }
    }

    @Test
    void clientAddressResolutionIsNotAffectedByTheFlags() {
        try (ApplicationContext ctx = ApplicationContext.run(flags(false, false))) {
            HttpClientAddressResolver resolver = ctx.getBean(HttpClientAddressResolver.class);
            assertEquals("203.0.113.7", resolver.resolve(HttpRequest.GET("/").header("X-Forwarded-For", "203.0.113.7")));
            assertEquals("198.51.100.9", resolver.resolve(HttpRequest.GET("/").header(HttpHeaders.FORWARDED, "for=198.51.100.9")));
        }
    }

    @AfterAll
    static void closeContexts() {
        CONTEXTS.values().forEach(ApplicationContext::close);
        CONTEXTS.clear();
    }

    private static String resolve(Map<String, Object> config, HttpRequest<?> request) {
        // one context per configuration: the resolver is stateless, so requests can share it
        return CONTEXTS.computeIfAbsent(Map.copyOf(config), ApplicationContext::run)
            .getBean(HttpHostResolver.class)
            .resolve(request);
    }

    private static Map<String, Object> flags(boolean allowForwarded, boolean allowXForwarded) {
        return Map.of(ALLOW_FORWARDED, allowForwarded, ALLOW_X_FORWARDED, allowXForwarded);
    }

    private static MutableHttpRequest<?> bothFamilies() {
        return HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=https")
            .header("X-Forwarded-Host", "xfwd.example.com")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Port", "8443");
    }

    private static MutableHttpRequest<?> forwardedOnly() {
        return HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header(HttpHeaders.FORWARDED, "host=fwd.example.com;proto=https");
    }

    private static MutableHttpRequest<?> xForwardedOnly() {
        return HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "origin.example.com")
            .header("X-Forwarded-Host", "xfwd.example.com")
            .header("X-Forwarded-Proto", "https")
            .header("X-Forwarded-Port", "8443");
    }
}
