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
package io.micronaut.http.server.tck.tests;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.tck.AssertionUtils;
import io.micronaut.http.tck.HttpResponseAssertion;
import io.micronaut.http.tck.ServerUnderTest;
import io.micronaut.http.tck.ServerUnderTestProviderUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@SuppressWarnings({
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
    "checkstyle:DesignForExtension"
})
public class FilterProxyTest {
    public static final String SPEC_NAME = "FilterProxyTest";
    public static final String PROP_MICRONAUT_SERVER_CORS_ENABLED = "micronaut.server.cors.enabled";

    @Test
    void testFiltersAreRunCorrectly() throws IOException {
        Map<String, Object> configuration = Map.of(
            PROP_MICRONAUT_SERVER_CORS_ENABLED, StringUtils.TRUE
        );
        try (ServerUnderTest server = ServerUnderTestProviderUtils.getServerUnderTestProvider().getServer(SPEC_NAME, configuration)) {
            HttpRequest<?> request = HttpRequest.GET("/filter-test/redirection");
            AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("OK")
                .headers(Collections.singletonMap("X-Test-Filter", StringUtils.TRUE))
                .build());
        }
    }

    @Controller("/ok")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestController {

        @Get
        String ok() {
            return "OK";
        }
    }

    @Filter("/filter-test/**")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class TestFilter implements HttpServerFilter {

        private final ProxyHttpClient client;
        private final EmbeddedServer embeddedServer;

        public TestFilter(
            ProxyHttpClient client,
            EmbeddedServer embeddedServer
        ) {
            this.client = client;
            this.embeddedServer = embeddedServer;
        }

        @Override
        public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {
            MutableHttpRequest<?> newRequest = request.mutate()
                .uri(b -> b
                    .scheme(embeddedServer.getScheme())
                    .host(embeddedServer.getHost())
                    .port(embeddedServer.getPort())
                    .replacePath("/ok")
                );
            Publisher<MutableHttpResponse<?>> proxyRequest = client.proxy(newRequest);
            return Flux.from(proxyRequest).map(httpResponse -> {
                    httpResponse.getHeaders().add("X-Test-Filter", "true");
                    return httpResponse;
                }
            );
        }
    }
}
