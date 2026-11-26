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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.PropertySource;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.HttpServerConfiguration.HostResolutionConfiguration;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.tree.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code micronaut.server.host-resolution.allow-forwarded} and {@code allow-xforwarded} allow ignoring
 * the forwarded headers for host resolution. Both default to {@code true}.
 */
class DefaultHttpHostResolverForwardedHeadersTest {

    private static final String PREFIX = "micronaut.server.host-resolution.";
    private static final String ALLOW_FORWARDED = PREFIX + "allow-forwarded";
    private static final String ALLOW_XFORWARDED = PREFIX + "allow-xforwarded";
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
        Map<String, Object> config = Map.of(ALLOW_FORWARDED, true, ALLOW_XFORWARDED, true);
        assertEquals("https://fwd.example.com", resolve(config, bothFamilies()));
        assertEquals("https://xfwd.example.com:8443", resolve(config, xForwardedOnly()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-xforwarded={1}")
    @CsvSource({
        "true,  true,  https://fwd.example.com",
        "false, true,  https://xfwd.example.com:8443",
        "true,  false, https://fwd.example.com",
        "false, false, http://origin.example.com",
    })
    void withBothFamiliesPresent(boolean allowForwarded, boolean allowXforwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXforwarded), bothFamilies()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-xforwarded={1}")
    @CsvSource({
        "true,  true,  https://fwd.example.com",
        "false, true,  http://origin.example.com",
        "true,  false, https://fwd.example.com",
        "false, false, http://origin.example.com",
    })
    void withOnlyTheForwardedHeaderPresent(boolean allowForwarded, boolean allowXforwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXforwarded), forwardedOnly()));
    }

    @ParameterizedTest(name = "allow-forwarded={0}, allow-xforwarded={1}")
    @CsvSource({
        "true,  true,  https://xfwd.example.com:8443",
        "false, true,  https://xfwd.example.com:8443",
        "true,  false, http://origin.example.com",
        "false, false, http://origin.example.com",
    })
    void withOnlyTheXForwardedHeadersPresent(boolean allowForwarded, boolean allowXforwarded, String expected) {
        assertEquals(expected, resolve(flags(allowForwarded, allowXforwarded), xForwardedOnly()));
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
        config.put(PREFIX + "host-header", "X-Forwarded-Host");
        assertEquals("http://xfwd.example.com", resolve(config, xForwardedOnly()));
    }

    @Test
    void theHostHeaderCanBeCombinedWithAConfiguredProtocolHeader() {
        // the configuration the guide shows for a proxy that overwrites X-Forwarded-Proto only
        Map<String, Object> config = Map.of(
            PREFIX + "host-header", "Host",
            PREFIX + "port-in-host", true,
            PREFIX + "protocol-header", "X-Forwarded-Proto");
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
        config.put(PREFIX + "host-header", "X-Forwarded-Host");
        config.put(PREFIX + "protocol-header", "X-Forwarded-Proto");
        config.put(PREFIX + "port-header", "X-Forwarded-Port");
        assertEquals("https://xfwd.example.com:8443", resolve(config, xForwardedOnly()));
    }

    @Test
    void aForwardedHostThatIsNotAllowedResolvesToTheDefaultHost() {
        Map<String, Object> config = Map.of(PREFIX + "allowed-hosts", "https://app\\.example\\.com");
        MutableHttpRequest<?> request = HttpRequest.GET("/")
            .header(HttpHeaders.HOST, "app.example.com")
            .header("X-Forwarded-Host", "other.example.com")
            .header("X-Forwarded-Proto", "https");
        assertEquals("http://localhost", resolve(config, request));
    }

    @Test
    void allowedHostsApplyToTheHostHeaderWhenTheForwardedHeadersAreIgnored() {
        Map<String, Object> config = new HashMap<>(flags(false, false));
        config.put(PREFIX + "allowed-hosts", "http://app\\.example\\.com");
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
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(PREFIX + "port-in-host", true))) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertTrue(configuration.isAllowForwarded());
            assertTrue(configuration.isAllowXForwarded());
            assertFalse(configuration.headersConfigured());
        }
    }

    @Test
    void bothFlagsBindFromTheirKebabCaseNames() {
        try (ApplicationContext ctx = ApplicationContext.run(flags(false, false))) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertFalse(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
            assertFalse(configuration.headersConfigured(), "the flags do not switch to the configured headers");
        }
    }

    // The configuration metadata, and so the configuration reference and IDE completion, lists the name
    // derived from the setter, allow-xforwarded. The spelling that splits "x" into its own word does not bind.
    // port-in-host is set so that the configuration bean exists whether or not the flag binds.
    // Revisit the allow-x-forwarded row deliberately if https://github.com/micronaut-projects/micronaut-core/issues/14583
    // makes a setter-level @Property alias viable.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "micronaut.server.host-resolution.allow-xforwarded,  false",
        "micronaut.server.host-resolution.allowXforwarded,   false",
        "micronaut.server.host-resolution.allowXForwarded,   false",
        "micronaut.server.host-resolution.allow-x-forwarded, true",
    })
    void theHyphenatedXForwardedSpellingIsTheOnlyOneThatDoesNotBind(String property, boolean expected) {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(property, false, PREFIX + "port-in-host", true))) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertTrue(configuration.isPortInHost());
            assertEquals(expected, configuration.isAllowXForwarded());
        }
    }

    // The configuration metadata feeds the configuration reference and IDE completion, and it derives each
    // name from the setter. Pinning the whole set makes a new or renamed property fail here, so that whoever
    // changes it also checks that the listed name binds. Every listed name is then bound to a value other
    // than its default. Revisit the pinned set deliberately if
    // https://github.com/micronaut-projects/micronaut-core/issues/14583 makes a setter-level @Property alias viable.
    @Test
    void theConfigurationMetadataListsTheNamesThatBind() throws IOException {
        Map<String, Object> nonDefaults = Map.of(
            PREFIX + "host-header", "X-Test-Host",
            PREFIX + "protocol-header", "X-Test-Proto",
            PREFIX + "port-header", "X-Test-Port",
            PREFIX + "port-in-host", true,
            PREFIX + "allowed-hosts", "https://app\\.example\\.com",
            ALLOW_FORWARDED, false,
            ALLOW_XFORWARDED, false);
        Set<String> names = configurationMetadataNames(HostResolutionConfiguration.class);
        assertFalse(names.isEmpty(), "no META-INF/spring-configuration-metadata.json on the test classpath lists "
            + HostResolutionConfiguration.class.getName());
        assertEquals(nonDefaults.keySet(), names);

        try (ApplicationContext ctx = ApplicationContext.run(nonDefaults)) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertEquals("X-Test-Host", configuration.getHostHeader());
            assertEquals("X-Test-Proto", configuration.getProtocolHeader());
            assertEquals("X-Test-Port", configuration.getPortHeader());
            assertTrue(configuration.isPortInHost());
            assertEquals(List.of("https://app\\.example\\.com"),
                configuration.getAllowedHosts().stream().map(Pattern::pattern).toList());
            assertFalse(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
        }
    }

    @Test
    void eachFlagBindsIndependently() {
        try (ApplicationContext ctx = ApplicationContext.run(Map.of(ALLOW_XFORWARDED, false))) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertTrue(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
        }
    }

    @Test
    void bothFlagsBindFromEnvironmentVariables() {
        PropertySource env = PropertySource.of("env", Map.of(
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_FORWARDED", "false",
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_XFORWARDED", "false"
        ), PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, PropertySource.Origin.of("env"));
        try (ApplicationContext ctx = ApplicationContext.builder().propertySources(env).start()) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertFalse(configuration.isAllowForwarded());
            assertFalse(configuration.isAllowXForwarded());
        }
    }

    // EnvironmentProperties expands each underscore to a dot or a hyphen, so the variable that separates X
    // from FORWARDED maps only to names such as allow-x-forwarded, which do not bind. The allow-forwarded
    // variable in the same source shows that the source itself is read.
    @Test
    void theEnvironmentVariableThatSeparatesXFromForwardedDoesNotBind() {
        PropertySource env = PropertySource.of("env", Map.of(
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_FORWARDED", "false",
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_X_FORWARDED", "false"
        ), PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, PropertySource.Origin.of("env"));
        try (ApplicationContext ctx = ApplicationContext.builder().propertySources(env).start()) {
            HostResolutionConfiguration configuration = hostResolution(ctx);
            assertFalse(configuration.isAllowForwarded());
            assertTrue(configuration.isAllowXForwarded());
        }
    }

    @Test
    void theHyphenatedXForwardedSpellingLogsAWarning() {
        List<String> warnings = warningsWhile(() -> ApplicationContext.run(Map.of(PREFIX + "allow-x-forwarded", false)));
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("micronaut.server.host-resolution.allow-x-forwarded"), warnings.get(0));
        assertTrue(warnings.get(0).contains("micronaut.server.host-resolution.allow-xforwarded"), warnings.get(0));
    }

    @Test
    void theEnvironmentVariableThatSeparatesXFromForwardedLogsAWarning() {
        PropertySource env = PropertySource.of("env", Map.of(
            "MICRONAUT_SERVER_HOST_RESOLUTION_ALLOW_X_FORWARDED", "false"
        ), PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE, PropertySource.Origin.of("env"));
        List<String> warnings = warningsWhile(() -> ApplicationContext.builder().propertySources(env).start());
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("micronaut.server.host-resolution.allow-x-forwarded"), warnings.get(0));
    }

    @Test
    void theSpellingThatBindsLogsNoWarning() {
        assertEquals(List.of(), warningsWhile(() -> ApplicationContext.run(flags(false, false))));
    }

    /**
     * @param contextSupplier Starts a context, which is closed after its host resolution configuration is created
     * @return The warnings that HttpServerConfiguration logged meanwhile
     */
    private static List<String> warningsWhile(Supplier<ApplicationContext> contextSupplier) {
        Logger logger = (Logger) LoggerFactory.getLogger(HttpServerConfiguration.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (ApplicationContext ctx = contextSupplier.get()) {
            hostResolution(ctx);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        return appender.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }

    @Test
    void clientAddressResolutionIsNotAffectedByTheFlags() {
        try (ApplicationContext ctx = ApplicationContext.run(flags(false, false))) {
            HttpClientAddressResolver resolver = ctx.getBean(HttpClientAddressResolver.class);
            assertEquals("203.0.113.7", resolver.resolve(HttpRequest.GET("/").header("X-Forwarded-For", "203.0.113.7")));
            assertEquals("198.51.100.9", resolver.resolve(HttpRequest.GET("/").header(HttpHeaders.FORWARDED, "for=198.51.100.9")));
        }
    }

    @Test
    void withoutHostResolutionPropertiesTheDefaultsOfTheConfigurationClassApply() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            // no host-resolution property is set, so the resolver sees no configuration bean
            assertNull(ctx.getBean(HttpServerConfiguration.class).getHostResolution());
        }
        HostResolutionConfiguration defaults = new HostResolutionConfiguration();
        assertEquals(defaults.isAllowForwarded() ? "https://fwd.example.com" : "http://origin.example.com",
            resolve(Map.of(), forwardedOnly()));
        assertEquals(defaults.isAllowXForwarded() ? "https://xfwd.example.com:8443" : "http://origin.example.com",
            resolve(Map.of(), xForwardedOnly()));
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

    private static HostResolutionConfiguration hostResolution(ApplicationContext ctx) {
        HostResolutionConfiguration configuration = ctx.getBean(HttpServerConfiguration.class).getHostResolution();
        assertNotNull(configuration, "setting a host-resolution property creates the host resolution configuration");
        return configuration;
    }

    private static Set<String> configurationMetadataNames(Class<?> sourceType) throws IOException {
        JsonMapper jsonMapper = JsonMapper.createDefault();
        Set<String> names = new HashSet<>();
        Enumeration<URL> resources = DefaultHttpHostResolverForwardedHeadersTest.class.getClassLoader()
            .getResources("META-INF/spring-configuration-metadata.json");
        for (URL resource : Collections.list(resources)) {
            try (InputStream in = resource.openStream()) {
                JsonNode properties = jsonMapper.readValue(in, JsonNode.class).get("properties");
                if (properties == null) {
                    continue;
                }
                for (JsonNode property : properties.values()) {
                    JsonNode type = property.get("sourceType");
                    if (type != null && sourceType.getName().equals(type.getStringValue())) {
                        names.add(property.get("name").getStringValue());
                    }
                }
            }
        }
        return names;
    }

    private static Map<String, Object> flags(boolean allowForwarded, boolean allowXforwarded) {
        return Map.of(ALLOW_FORWARDED, allowForwarded, ALLOW_XFORWARDED, allowXforwarded);
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
