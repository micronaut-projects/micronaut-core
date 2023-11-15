/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server.tck.tests.cors;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.server.tck.CorsAssertion;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.RequestSupplier;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public class CorsSimpleRequestTest {

    private static final String SPECNAME = "CorsSimpleRequestTest";
    private static final String PROPERTY_MICRONAUT_SERVER_CORS_ENABLED = "micronaut.server.cors.enabled";
    private static final String PROPERTY_MICRONAUT_SERVER_CORS_LOCALHOST_PASS_THROUGH = "micronaut.server.cors.localhost-pass-through";

    /**
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/security/advisories/GHSA-583g-g682-crxf">GHSA-583g-g682-crxf</a>
     *
     * A malicious/compromised website can make HTTP requests to localhost. Normally, such requests would trigger a CORS preflight check which would prevent the request; however, some requests are "simple" and do not require a preflight check. These endpoints, if enabled and not secured, are vulnerable to being triggered.
     * Example with Javascript:
     * <pre>
     * let url = "http://localhost:8080/refresh";
     * let body = new FormData();
     * body.append("force", "true");
     * fetch(url, { method: "POST", body });
     * </pre>
     * @throws IOException may throw the try for resources
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestNotAllowedForLocalhostAndAny() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequest("https://foo.com"),
            CorsSimpleRequestTest::isForbidden
        );
    }

    /**
     * Test that a simple request is allowed for localhost and origin:any when specifically turned off.
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/pull/8751">PR-8751</a>
     *
     * @throws IOException
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestAllowedForLocalhostAndAnyWhenSpecificallyTurnedOff() throws IOException {
        asserts(SPECNAME,
            Map.of(
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
                PROPERTY_MICRONAUT_SERVER_CORS_LOCALHOST_PASS_THROUGH, StringUtils.TRUE
            ),
            createRequest("https://foo.com"),
            CorsSimpleRequestTest::isSuccessful
        );
    }

    /**
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/security/advisories/GHSA-583g-g682-crxf">GHSA-583g-g682-crxf</a>
     *
     * A malicious/compromised website can make HTTP requests to 127.0.0.1. Normally, such requests would trigger a CORS preflight check which would prevent the request; however, some requests are "simple" and do not require a preflight check. These endpoints, if enabled and not secured, are vulnerable to being triggered.
     * Example with Javascript:
     * <pre>
     * let url = "http://127.0.0.1:8080/refresh";
     * let body = new FormData();
     * body.append("force", "true");
     * fetch(url, { method: "POST", body });
     * </pre>
     * @throws IOException may throw the try for resources
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestNotAllowedFor127AndAny() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequestFor("127.0.0.1", "https://foo.com"),
            CorsSimpleRequestTest::isForbidden
        );
    }

    /**
     * It should not deny a cors request coming from a localhost origin if the micronaut application resolved host is localhost.
     * @throws IOException scenario step fails
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestAllowedForLocalhostAndOriginLocalhost() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequest("http://localhost:8000"),
            CorsSimpleRequestTest::isSuccessful
        );
    }

    /**
     * A request to localhost with an origin of 127.0.0.1 should be allowed as they are both local.
     *
     * @throws IOException
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestAllowedForLocalhostAnd127Origin() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequestFor("localhost", "http://127.0.0.1:8000"),
            CorsSimpleRequestTest::isSuccessful
        );
    }

    /**
     * Spoof attempt with origin should fail.
     *
     * @throws IOException
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestFailsForLocalhostAndSpoofed127Origin() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequestFor("localhost", "http://127.0.0.1.hac0r.com:8000"),
            CorsSimpleRequestTest::isForbidden
        );
    }

    /**
     * A request to 127.0.0.1 with an origin of localhost should succeed as they're both local.
     *
     * @throws IOException
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestAllowedFor127RequestAndLocalhostOrigin() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap(PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE),
            createRequestFor("127.0.0.1", "http://localhost:8000"),
            CorsSimpleRequestTest::isSuccessful
        );
    }

    /**
     * CORS Simple request for localhost can be allowed via configuration.
     * @throws IOException may throw the try for resources
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestForLocalhostCanBeAllowedViaConfiguration() throws IOException {
        asserts(SPECNAME,
            Map.of(
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins", Collections.singletonList("https://foo.com")
            ),
            createRequest("https://foo.com"),
            CorsSimpleRequestTest::isSuccessfulCorsAssertion
        );
    }

    /**
     * CORS Simple request for localhost can be allowed via configuration of a regex.
     * @throws IOException may throw the try for resources
     */
    @Test
    // "https://github.com/micronaut-projects/micronaut-core/issues/9423")
    @Tag("multipart")
    void corsSimpleRequestForLocalhostCanBeAllowedViaRegexConfiguration() throws IOException {
        asserts(SPECNAME,
            Map.of(
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins-regex", Collections.singletonList("^http(|s):\\/\\/foo\\.com$")
            ),
            createRequest("https://foo.com"),
            CorsSimpleRequestTest::isSuccessfulCorsAssertion
        );
    }

    /**
     * CORS Simple request for localhost can be forbidden via configuration of a regex.
     * @throws IOException may throw the try for resources
     */
    @Test
    // "https://github.com/micronaut-projects/micronaut-core/issues/9423")
    @Tag("multipart")
    void corsSimpleRequestForLocalhostForbiddenViaRegexConfiguration() throws IOException {
        asserts(SPECNAME,
            Map.of(
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins-regex", Collections.singletonList("^http(|s):\\/\\/foo\\.com$")
            ),
            createRequest("https://bar.com"),
            CorsSimpleRequestTest::isForbidden
        );
    }

    /**
     * CORS Simple request for localhost can be allowed via configuration, with both allowed-origin and allowed-origin-regex.
     * @throws IOException may throw the try for resources
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestForLocalhostCanBeAllowedViaConfigurationWithRegexToo() throws IOException {
        Map<String, Object> config = Map.of(
            PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
            "micronaut.server.cors.configurations.foo.allowed-origins-regex", Collections.singletonList("^http(|s):\\/\\/foo\\.com$"),
            "micronaut.server.cors.configurations.foo.allowed-origins", Collections.singletonList("https://bar.com")
        );
        assertAll(
            () -> asserts(SPECNAME, config,
                createRequest("https://foo.com"),
                (server, request) -> {
                    RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
                    assertEquals(0, refreshCounter.getRefreshCount());

                    AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .assertResponse(response -> CorsAssertion.builder()
                            .vary("Origin")
                            .allowCredentials()
                            .allowOrigin("https://foo.com")
                            .build()
                            .validate(response))
                        .build());
                    assertEquals(1, refreshCounter.getRefreshCount());
                }),
            () -> asserts(SPECNAME, config,
                createRequest("http://foo.com"),
                (server, request) -> {
                    RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
                    assertEquals(0, refreshCounter.getRefreshCount());

                    AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .assertResponse(response -> CorsAssertion.builder()
                            .vary("Origin")
                            .allowCredentials()
                            .allowOrigin("http://foo.com")
                            .build()
                            .validate(response))
                        .build());
                    assertEquals(1, refreshCounter.getRefreshCount());
                }),
            () -> asserts(SPECNAME, config,
                createRequest("https://bar.com"),
                (server, request) -> {
                    RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
                    assertEquals(0, refreshCounter.getRefreshCount());

                    AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                        .status(HttpStatus.OK)
                        .assertResponse(response -> CorsAssertion.builder()
                            .vary("Origin")
                            .allowCredentials()
                            .allowOrigin("https://bar.com")
                            .build()
                            .validate(response))
                        .build());
                    assertEquals(1, refreshCounter.getRefreshCount());
                })
        );
    }

    /**
     * WHen using allowed-origins-regex, allowed-origins should not default to ANY.
     * @throws IOException scenario step fails
     */
    @Test
    @Tag("multipart")
    void corsSimpleRequestForAllowedRegexDoesNotDefaultToAllAllowedorigins() throws IOException {
        asserts(SPECNAME,
            Map.of(
                PROPERTY_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE,
                "micronaut.server.cors.configurations.foo.allowed-origins-regex", Collections.singletonList("^http(|s):\\/\\/foo\\.com$")
            ),
            createRequest("https://bar.com"),
            CorsSimpleRequestTest::isForbidden
        );
    }

    private RequestSupplier createRequestFor(String host, String origin) {
        return server -> createRequest(server.getPort().map(p -> "http://" + host + ":" + p + "/refresh").orElseThrow(() -> new RuntimeException("Unknown port for " + server)), origin);
    }

    static void isForbidden(ServerUnderTest server, HttpRequest<?> request) {
        RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
        assertEquals(0, refreshCounter.getRefreshCount());
        AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.FORBIDDEN)
            .assertResponse(response -> assertFalse(response.getHeaders().contains("Vary")))
            .build());
        assertEquals(0, refreshCounter.getRefreshCount());
    }

    static void isSuccessful(ServerUnderTest server, HttpRequest<?> request) {
        RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
        assertEquals(0, refreshCounter.getRefreshCount());
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .build());
        assertEquals(1, refreshCounter.getRefreshCount());
    }

    static void isSuccessfulCorsAssertion(ServerUnderTest server, HttpRequest<?> request) {
        RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
        assertEquals(0, refreshCounter.getRefreshCount());
        AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
            .status(HttpStatus.OK)
            .assertResponse(response -> CorsAssertion.builder()
                .vary("Origin")
                .allowCredentials()
                .allowOrigin("https://foo.com")
                .build()
                .validate(response))
            .build());
        assertEquals(1, refreshCounter.getRefreshCount());
    }

    static HttpRequest<?> createRequest(String origin) {
        return createRequest("/refresh", origin);
    }

    static HttpRequest<?> createRequest(String uri, String origin) {
        return HttpRequest.POST(uri, MultipartBody.builder().addPart("force", StringUtils.TRUE).build())
            .header("Content-Type", "multipart/form-data; boundary=----WebKitFormBoundarywxiDZy8kMlSE59h1")
            .header("Origin", origin)
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .header("Accept", "*/*")
            .header("User-Agent", "Mozilla / 5.0 (Macintosh; Intel Mac OS X 10_15_7)AppleWebKit / 605.1 .15 (KHTML, like Gecko)Version / 16.1 Safari / 605.1 .15")
            .header("Referer", origin)
            .header("Accept-Language", "en - GB, en")
            .header("content-length", "140");
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller
    static class RefreshController {
        @Inject
        ApplicationEventPublisher<RefreshEvent> refreshEventApplicationEventPublisher;

        @Consumes(MediaType.MULTIPART_FORM_DATA)
        @Post("/refresh")
        @Status(HttpStatus.OK)
        void refresh() {
            refreshEventApplicationEventPublisher.publishEvent(new RefreshEvent());
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Singleton
    static class RefreshCounter implements ApplicationEventListener<RefreshEvent> {
        private int refreshCount = 0;

        @Override
        public void onApplicationEvent(RefreshEvent event) {
            refreshCount++;
        }

        public int getRefreshCount() {
            return refreshCount;
        }
    }
}
