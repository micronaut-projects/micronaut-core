/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.runtime.context.scope.refresh.RefreshEvent;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.micronaut.http.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public class SimpleRequestWithCorsNotEnabledTest {

    private static final String SPECNAME = "SimpleRequestWithCorsNotEnabledTest";
    private static final String PROPERTY_MICRONAUT_SERVER_CORS_LOCALHOST_PASS_THROUGH = "micronaut.server.cors.localhost-pass-through";
    private static final String ENDPOINTS_REFRESH_ENABLED = "endpoints.refresh.enabled";
    private static final String ENDPOINTS_REFRESH_SENSITIVE = "endpoints.refresh.sensitive";
    private Map<String, Object> BASE_CONFIG = Map.of(
        ENDPOINTS_REFRESH_ENABLED, StringUtils.TRUE,
        ENDPOINTS_REFRESH_SENSITIVE, StringUtils.FALSE);

    /**
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/security/advisories/GHSA-583g-g682-crxf">GHSA-583g-g682-crxf</a>
     * A malicious/compromised website can make HTTP requests to localhost. This test verifies a CORS simple request is denied when invoked against a Micronaut application running in localhost without cors enabled.
     * @throws IOException scenario step fails
     */
    @Test
    void corsSimpleRequestNotAllowedForLocalhostAndAny() throws IOException {
        asserts(SPECNAME,
            BASE_CONFIG,
            createRequest("https://sdelamo.github.io"),
            this::assertRefreshEndpointNotHit);
    }

    /**
     * This test verifies a CORS simple request is allowed when invoked against a Micronaut application running in localhost without cors enabled but with localhost-pass-through switched on.
     * @see <a href="https://github.com/micronaut-projects/micronaut-core/pull/8751">PR-8751</a>
     *
     * @throws IOException
     */
    @Test
    void corsSimpleRequestAllowedForLocalhostAndAnyWhenConfiguredToAllowIt() throws IOException {
        Map<String, Object> config = new HashMap<>(BASE_CONFIG);
        config.put(PROPERTY_MICRONAUT_SERVER_CORS_LOCALHOST_PASS_THROUGH, StringUtils.TRUE);
        asserts(SPECNAME,
            config,
            createRequest("https://sdelamo.github.io"),
            this::assertRefreshEndpointHit);
    }

    /**
     * It should not deny a cors request coming from a localhost origin if the micronaut application resolved host is localhost.
     * @throws IOException scenario step fails
     */
    @Test
    void corsSimpleRequestAllowedForLocalhostAndOriginLocalhost() throws IOException {
        asserts(SPECNAME,
            BASE_CONFIG,
            createRequest("http://localhost:8000"),
            this::assertRefreshEndpointHit);
    }

    private void assertRefreshEndpointHit(ServerUnderTest server, HttpRequest<?> request) {
        assertRefreshEndpointInvocation(server, request, HttpStatus.OK, 1);
    }

    private void assertRefreshEndpointNotHit(ServerUnderTest server, HttpRequest<?> request) {
        assertRefreshEndpointInvocation(server, request, HttpStatus.FORBIDDEN, 0);
    }

    private void assertRefreshEndpointInvocation(ServerUnderTest server, HttpRequest<?> request,
                                                 HttpStatus expectedStatus,
                                                 int invocations) {
        RefreshCounter refreshCounter = server.getApplicationContext().getBean(RefreshCounter.class);
        assertEquals(0, refreshCounter.getRefreshCount());
        if (expectedStatus.getCode() >= 400) {
            AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(expectedStatus)
                .assertResponse(response -> assertFalse(response.getHeaders().contains("Vary")))
                .build());
        } else {
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .build());
        }
        assertEquals(invocations, refreshCounter.getRefreshCount());
        refreshCounter.reset();
    }

    private static HttpRequest<?> createRequest(String origin) {
        return HttpRequest.POST("/refresh", Map.of("force", StringUtils.TRUE))
            .header("Accept", "*/*")
            .header("Accept-Encoding", "gzip, deflate, br")
            .header("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
            .header("Connection", "keep-alive")
            .header("Host", "localhost:8080")
            .header("Origin", origin)
            .header("sec-ch-ua", "\"Not?A_Brand\";v=\"8\", \"Chromium\";v=\"108\", \"Google Chrome\";v=\"108\"")
            .header("sec-ch-ua-mobile", "?0")
            .header("sec-ch-ua-platform", "\"macOS\"")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "cross-site")
            .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
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

        public void reset() {
            refreshCount = 0;
        }
    }
}
