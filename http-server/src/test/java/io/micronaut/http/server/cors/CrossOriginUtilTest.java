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
package io.micronaut.http.server.cors;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteMatch;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.micronaut.http.HttpHeaders.ORIGIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossOriginUtilTest {
    private static final String REQUEST_ORIGIN = "https://example.com";

    @Test
    void constructorIsNotPubliclyAccessible() throws ReflectiveOperationException {
        Constructor<CrossOriginUtil> constructor = CrossOriginUtil.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertNotNull(constructor.newInstance());
    }

    @Test
    void matchesConfiguredOrigins() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();

        assertTrue(CrossOriginUtil.matchesOrigin(configuration, REQUEST_ORIGIN));

        configuration.setAllowedOrigins(List.of(REQUEST_ORIGIN));
        assertTrue(CrossOriginUtil.matchesOrigin(configuration, REQUEST_ORIGIN));
        assertFalse(CrossOriginUtil.matchesOrigin(configuration, "https://other.example.com"));

        configuration.setAllowedOrigins(List.of());
        assertFalse(CrossOriginUtil.matchesOrigin(configuration, REQUEST_ORIGIN));

        configuration.setAllowedOriginsRegex("https://.*\\.example\\.com");
        assertTrue(CrossOriginUtil.matchesOrigin(configuration, "https://api.example.com"));
        assertFalse(CrossOriginUtil.matchesOrigin(configuration, "https://example.org"));

        configuration.setAllowedOrigins(List.of(REQUEST_ORIGIN));
        assertTrue(CrossOriginUtil.matchesOrigin(configuration, REQUEST_ORIGIN));
    }

    @Test
    void matchesOriginRegexAgainstTheCompleteOrigin() {
        assertTrue(CrossOriginUtil.matchesOrigin("https://.*\\.example\\.com", "https://api.example.com"));
        assertFalse(CrossOriginUtil.matchesOrigin("example", "https://example.com"));
    }

    @Test
    void identifiesWildcardValuesAndMethods() {
        assertTrue(CrossOriginUtil.isAny(CorsOriginConfiguration.ANY));
        assertFalse(CrossOriginUtil.isAny(List.of("value")));
        assertTrue(CrossOriginUtil.isAnyMethod(CorsOriginConfiguration.ANY_METHOD));
        assertFalse(CrossOriginUtil.isAnyMethod(List.of(HttpMethod.GET)));
    }

    @Test
    void validatesAllowedMethods() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        assertTrue(CrossOriginUtil.methodAllowed(configuration, HttpMethod.DELETE));

        configuration.setAllowedMethods(List.of(HttpMethod.GET));
        assertTrue(CrossOriginUtil.methodAllowed(configuration, HttpMethod.GET));
        assertFalse(CrossOriginUtil.methodAllowed(configuration, HttpMethod.POST));
    }

    @Test
    void resolvesMethodForActualAndPreflightRequests() {
        assertEquals(HttpMethod.POST, CrossOriginUtil.methodToMatch(HttpRequest.POST("/", "body")));
        assertEquals(HttpMethod.PUT, CrossOriginUtil.methodToMatch(preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "PUT")));
        assertEquals(HttpMethod.OPTIONS, CrossOriginUtil.methodToMatch(preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "invalid")));
    }

    @Test
    void validatesRequestedHeaders() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        assertTrue(CrossOriginUtil.hasAllowedHeaders(preflightRequest(), configuration));

        configuration.setAllowedHeaders(List.of("X-Allowed", "X-Second"));
        assertFalse(CrossOriginUtil.hasAllowedHeaders(preflightRequest(), configuration));
        assertTrue(CrossOriginUtil.hasAllowedHeaders(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_HEADERS, "x-allowed,X-SECOND"),
            configuration
        ));
        assertFalse(CrossOriginUtil.hasAllowedHeaders(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_HEADERS, "X-Denied"),
            configuration
        ));
    }

    @Test
    void validatesAndReturnsRequestedMethod() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedMethods(List.of(HttpMethod.GET));

        assertEquals(HttpMethod.GET, CrossOriginUtil.validateMethodToMatch(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "GET"),
            configuration
        ).orElseThrow());
        assertTrue(CrossOriginUtil.validateMethodToMatch(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "POST"),
            configuration
        ).isEmpty());
    }

    @Test
    void matchesActualAndPreflightRequestMethods() {
        CorsOriginConfiguration configuration = new CorsOriginConfiguration();
        configuration.setAllowedMethods(List.of(HttpMethod.GET));

        assertTrue(CrossOriginUtil.matchesMethod(HttpRequest.GET("/"), configuration));
        assertFalse(CrossOriginUtil.matchesMethod(HttpRequest.POST("/", "body"), configuration));
        assertTrue(CrossOriginUtil.matchesMethod(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "GET"),
            configuration
        ));
        assertFalse(CrossOriginUtil.matchesMethod(
            preflightRequest().header(ACCESS_CONTROL_REQUEST_METHOD, "DELETE"),
            configuration
        ));
    }

    @Test
    void resolvesConfigurationFromRequestRouteMetadata() {
        MutableHttpRequest<?> request = HttpRequest.GET("/");
        assertTrue(CrossOriginUtil.getCorsOriginConfigurationForRequest(request).isEmpty());

        AnnotationMetadata metadata = crossOriginMetadata(Map.of());
        RouteAttributes.setRouteMatch(request, routeMatch(metadata));

        assertTrue(CrossOriginUtil.getCorsOriginConfigurationForRequest(request).isPresent());
    }

    @Test
    void resolvesConfigurationFromAnnotationMetadataProvider() {
        AnnotationMetadataProvider withoutCrossOrigin = metadataProvider(AnnotationMetadata.EMPTY_METADATA);
        AnnotationMetadataProvider withCrossOrigin = metadataProvider(crossOriginMetadata(Map.of()));

        assertTrue(CrossOriginUtil.getCorsOriginConfigurationForAnnotationMetadataProvider(withoutCrossOrigin).isEmpty());
        assertTrue(CrossOriginUtil.getCorsOriginConfigurationForAnnotationMetadataProvider(withCrossOrigin).isPresent());
    }

    @Test
    void convertsDefaultCrossOriginAnnotationValues() {
        assertTrue(CrossOriginUtil.getCorsOriginConfiguration(AnnotationMetadata.EMPTY_METADATA).isEmpty());

        CorsOriginConfiguration configuration = CrossOriginUtil.getCorsOriginConfiguration(
            crossOriginMetadata(Map.of())
        ).orElseThrow();

        assertEquals(CorsOriginConfiguration.ANY, configuration.getAllowedOrigins());
        assertEquals(CorsOriginConfiguration.ANY, configuration.getAllowedHeaders());
        assertEquals(List.of(), configuration.getExposedHeaders());
        assertEquals(CorsOriginConfiguration.ANY_METHOD, configuration.getAllowedMethods());
        assertFalse(configuration.isAllowCredentials());
        assertTrue(configuration.isAllowPrivateNetwork());
        assertEquals(1800L, configuration.getMaxAge());
    }

    @Test
    void convertsExplicitCrossOriginAnnotationValues() {
        CorsOriginConfiguration configuration = CrossOriginUtil.getCorsOriginConfiguration(crossOriginMetadata(Map.of(
            CrossOriginUtil.MEMBER_ALLOWED_ORIGINS, new String[]{REQUEST_ORIGIN},
            CrossOriginUtil.MEMBER_ALLOWED_ORIGINS_REGEX, "https://.*\\.example\\.com",
            CrossOriginUtil.MEMBER_ALLOWED_HEADERS, new String[]{"X-Allowed"},
            CrossOriginUtil.MEMBER_EXPOSED_HEADERS, new String[]{"X-Exposed"},
            CrossOriginUtil.MEMBER_ALLOWED_METHODS, new String[]{"GET", "CUSTOM"},
            CrossOriginUtil.MEMBER_ALLOW_CREDENTIALS, true,
            CrossOriginUtil.MEMBER_ALLOW_PRIVATE_NETWORK, false,
            CrossOriginUtil.MEMBER_MAX_AGE, 42L
        ))).orElseThrow();

        assertEquals(List.of(REQUEST_ORIGIN), configuration.getAllowedOrigins());
        assertEquals("https://.*\\.example\\.com", configuration.getAllowedOriginsRegex().orElseThrow());
        assertEquals(List.of("X-Allowed"), configuration.getAllowedHeaders());
        assertEquals(List.of("X-Exposed"), configuration.getExposedHeaders());
        assertEquals(List.of(HttpMethod.GET), configuration.getAllowedMethods());
        assertTrue(configuration.isAllowCredentials());
        assertFalse(configuration.isAllowPrivateNetwork());
        assertEquals(42L, configuration.getMaxAge());
    }

    @Test
    void regexWithoutExplicitOriginsDoesNotEnableWildcardOrigin() {
        CorsOriginConfiguration configuration = CrossOriginUtil.getCorsOriginConfiguration(crossOriginMetadata(Map.of(
            CrossOriginUtil.MEMBER_ALLOWED_ORIGINS_REGEX, "https://allowed\\.example\\.com"
        ))).orElseThrow();

        assertEquals(List.of(), configuration.getAllowedOrigins());
        assertFalse(CrossOriginUtil.matchesOrigin(configuration, "https://denied.example.com"));
    }

    @Test
    void explicitOriginsWithoutRegexReplaceTheWildcardDefault() {
        CorsOriginConfiguration configuration = CrossOriginUtil.getCorsOriginConfiguration(crossOriginMetadata(Map.of(
            CrossOriginUtil.MEMBER_ALLOWED_ORIGINS, new String[]{REQUEST_ORIGIN}
        ))).orElseThrow();

        assertEquals(List.of(REQUEST_ORIGIN), configuration.getAllowedOrigins());
        assertTrue(configuration.getAllowedOriginsRegex().isEmpty());
    }

    private static MutableHttpRequest<?> preflightRequest() {
        return HttpRequest.OPTIONS("/").header(ORIGIN, REQUEST_ORIGIN);
    }

    private static AnnotationMetadata crossOriginMetadata(Map<CharSequence, Object> values) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addDeclaredAnnotation(CrossOrigin.class.getName(), values);
        return metadata;
    }

    private static AnnotationMetadataProvider metadataProvider(AnnotationMetadata metadata) {
        return new AnnotationMetadataProvider() {
            @Override
            public AnnotationMetadata getAnnotationMetadata() {
                return metadata;
            }
        };
    }

    private static RouteMatch<?> routeMatch(AnnotationMetadata metadata) {
        return (RouteMatch<?>) Proxy.newProxyInstance(
            CrossOriginUtilTest.class.getClassLoader(),
            new Class<?>[]{RouteMatch.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("getAnnotationMetadata")) {
                    return metadata;
                }
                throw new UnsupportedOperationException(method.getName());
            }
        );
    }
}
